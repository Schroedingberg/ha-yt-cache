(ns youtube-to-media.core
  (:require [babashka.fs :as fs]
            [babashka.process :refer [process]]
            [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.server :as srv]
            [org.httpkit.client :as http]
            [clojure.java.io :as io]))

(def download-directory (or (System/getenv "DOWNLOAD_DIRECTORY") "/share/youtube-to-media"))
;; Seam for tests: swap this out to fake the subprocess runner.
(def process-fn process)
(defonce jobs (atom {}))
(defonce worker-lock (Object.))
(defonce server (atom nil))
(defonce logged-progress (atom {}))

(defn- log-event! [event details]
  (println (pr-str (assoc details :event event)))
  (flush))

(defn set-job! [id & kvs]
  (swap! jobs update id #(apply assoc % kvs)))

(defn notify! [job]
  (try
    @(http/post "http://supervisor/core/api/events/download_manager_finished"
                {:headers {"Authorization" (str "Bearer " (System/getenv "SUPERVISOR_TOKEN"))}
                 :body (json/generate-string (select-keys job [:url :state :last]))
                 :timeout 5000})
    (catch Exception _ nil)))

(defn- update-download-line! [id line]
  (if-let [[_ percent] (re-find #"(\d+(?:\.\d+)?)%" line)]
    (let [progress (parse-double percent)
          progress-bucket (long progress)
          previous-bucket (get @logged-progress id -1)]
      (set-job! id :progress progress)
      (when (> progress-bucket previous-bucket)
        (swap! logged-progress assoc id progress-bucket)
        (log-event! :download/progress {:job-id id :percent progress-bucket})))
    (set-job! id :last line)))

(defn- stream-download-output! [id output]
  (with-open [reader (io/reader output)]
    (doseq [line (line-seq reader)]
      (update-download-line! id line))))

(defn redacted-error-line [job]
  (when-let [line (:last job)]
    (if-let [url (:url job)]
      (str/replace line (re-pattern (java.util.regex.Pattern/quote url)) "[redacted URL]")
      line)))

(defn download! [id]
  (fs/create-dirs download-directory)
  (set-job! id :state :downloading)
  (log-event! :download/started {:job-id id})
  (let [download-process (process-fn {:out :stream :err :out}
                                     "uvx" "--from" "yt-dlp[default]"
                                     "--with" "bgutil-ytdlp-pot-provider"
                                     "yt-dlp"
                                     "--no-continue" "--newline"
                                     "--js-runtimes" "node"
                                     "--extractor-args" "youtube:player_client=mweb,web_embedded,web_safari"
                                     "--extractor-args" "youtubepot-bgutilscript:server_home=/usr/share/bgutil-ytdlp-pot-provider/server"
                                     "-P" download-directory "--" (:url (@jobs id)))
        output-future (future (stream-download-output! id (:out download-process)))
        exit-code (:exit @download-process)
        state (if (zero? exit-code) :done :failed)]
    @output-future
    (set-job! id :state state)
    (swap! logged-progress dissoc id)
    (if (= state :done)
      (log-event! :download/completed {:job-id id :exit-code exit-code})
      (log-event! :download/failed
                  {:job-id id
                   :exit-code exit-code
                   :error (redacted-error-line (@jobs id))}))
    (notify! (@jobs id))))

(defn- run-job! [id]
  (try
    (download! id)
    (catch Exception error
      (set-job! id :state :failed :last (ex-message error))
      (swap! logged-progress dissoc id)
      (log-event! :download/error {:job-id id :error-type (str (class error))}))))

(defn enqueue! [url]
  (let [id (str (random-uuid))]
    (swap! jobs assoc id {:url url :state :queued :progress 0})
    (log-event! :download/queued {:job-id id})
    (future (locking worker-lock (run-job! id)))
    id))

(def page "<!doctype html><meta charset=utf-8>
<form onsubmit=\"fetch('enqueue',{method:'POST',body:u.value});u.value='';return false\">
<input id=u size=60> <button>Add</button></form><pre id=o></pre>
<script>setInterval(async()=>{o.textContent=(await(await fetch('jobs')).json())
.map(x=>`${x.state} ${Math.round(x.progress)}% ${x.url} ${x.state=='failed'?x.last:''}`).join('\\n')},1500)</script>")

(defn valid-url? [url]
  (try
    (let [parsed (java.net.URL. url)
          protocol (.getProtocol parsed)
          host (.getHost parsed)]
      (and (#{"http" "https"} protocol)
           host
           (or (= host "youtu.be")
               (= host "youtube.com")
               (str/ends-with? host ".youtube.com"))))
    (catch Exception _ false)))

(defn- enqueue-request [body]
  (let [limit 8192
        bytes (.readNBytes ^java.io.InputStream (io/input-stream body) (inc limit))
        url (String. bytes 0 (min (alength bytes) limit) "UTF-8")]
    (cond
      (> (alength bytes) limit) {:status 413 :body "body too large"}
      (valid-url? url) {:status 202 :body (enqueue! url)}
      :else {:status 400 :body "invalid url"})))

(defn- request-authorized? [{:keys [headers]}]
  (let [token (System/getenv "SUPERVISOR_TOKEN")
        auth (get headers "authorization")
        ingress-path (get headers "x-ingress-path")]
    (or (nil? token)
        (= auth (str "Bearer " token))
        (some? ingress-path))))

(defn handler [{:keys [uri request-method] :as request}]
  (if (= [:get "/healthz"] [request-method uri])
    {:status 200 :body (json/generate-string {:status "ok"})}
    (if-not (request-authorized? request)
      {:status 401 :body "unauthorized"}
      (case [request-method uri]
        [:get "/"] {:headers {"Content-Type" "text/html"} :body page}
        [:get "/jobs"] {:body
                        (json/generate-string (vec (vals @jobs)))}
        [:post "/enqueue"] (enqueue-request (:body request))
        {:status 404}))))

(defn start-server!
  ([] (start-server! 8099))
  ([port]
   (or @server
       (reset! server (srv/run-server handler {:port port})))))

(defn stop-server! []
  (when-let [stop! @server]
    (stop!)
    (reset! server nil)))

(defn -main [& _]
  (let [port (parse-long (or (System/getenv "PORT") "8099"))]
    (start-server! port)
    (log-event! :server/started {:port port})
    @(promise)))
