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

(def ^:private yt-dlp-command
  ["uvx" "--from" "yt-dlp[default]"
   "--with" "bgutil-ytdlp-pot-provider"
   "yt-dlp"
   "--no-continue" "--newline"
   "--js-runtimes" "node"
   "--extractor-args" "youtube:player_client=mweb,web_embedded,web_safari"
   "--extractor-args" "youtubepot-bgutilscript:server_home=/usr/share/bgutil-ytdlp-pot-provider/server"])

(defn- download-args [url]
  (into yt-dlp-command ["-P" download-directory "--" url]))

(defonce jobs (atom {}))
(defonce job-queue (java.util.concurrent.LinkedBlockingQueue.))
(defonce server (atom nil))

(defn- log-event! [event details]
  (println (pr-str (assoc details :event event)))
  (flush))

(defn- set-job!
  "Merge key/value pairs into job id's state."
  [id & kvs]
  (swap! jobs update id #(apply assoc % kvs)))

(defn notify!
  "Post job completion to the supervisor's download_manager_finished event."
  [job]
  (try
    @(http/post "http://supervisor/core/api/events/download_manager_finished"
                {:headers {"Authorization" (str "Bearer " (System/getenv "SUPERVISOR_TOKEN"))}
                 :body (json/generate-string (select-keys job [:url :state :last]))
                 :timeout 5000})
    (catch Exception _ nil)))

(defn- update-download-line!
  "Update job id from a line of yt-dlp output: progress lines set :progress
  (logging once per whole percent); all other lines go to :last."
  [id line]
  (if-let [[_ percent] (re-find #"(\d+(?:\.\d+)?)%" line)]
    (let [progress (parse-double percent)
          bucket (long progress)
          ;; Read the last recorded percent before overwriting it below.
          previous (long (get-in @jobs [id :progress] 0))]
      (set-job! id :progress progress)
      (when (> bucket previous)
        (log-event! :download/progress {:job-id id :percent bucket})))
    (set-job! id :last line)))

(defn- stream-download-output! [id output]
  (with-open [reader (io/reader output)]
    (doseq [line (line-seq reader)]
      (update-download-line! id line))))

(defn download!
  "Run yt-dlp for job id, streaming output and recording the outcome."
  [id]
  (fs/create-dirs download-directory)
  (set-job! id :state :downloading)
  (log-event! :download/started {:job-id id})
  (let [url (:url (get @jobs id))
        download-process (apply process-fn {:out :stream :err :out} (download-args url))
        ;; Read output on a separate thread so a slow reader can't deadlock
        ;; the subprocess (whose stdout pipe fills up).
        output-future (future (stream-download-output! id (:out download-process)))
        exit-code (:exit @download-process)
        state (if (zero? exit-code) :done :failed)]
    @output-future
    (set-job! id :state state)
    (let [job (get @jobs id)]
      (case state
        :done   (log-event! :download/completed {:job-id id :exit-code exit-code})
        :failed (log-event! :download/failed
                            {:job-id id :exit-code exit-code :error (:last job)}))
      (notify! job))))

(defn- run-job! [id]
  (try
    (download! id)
    (catch Exception error
      (set-job! id :state :failed :last (ex-message error))
      (log-event! :download/error {:job-id id :error-type (str (class error))}))))

(defonce download-worker
  ;; One daemon thread runs queued jobs one at a time, in FIFO order.
  (doto (Thread. (fn []
                   (while true
                     (run-job! (.take ^java.util.concurrent.BlockingQueue job-queue)))))
    (.setDaemon true)
    (.start)))

(defn enqueue!
  "Queue url for download and return the new job id."
  [url]
  (let [id (str (random-uuid))]
    (swap! jobs assoc id {:url url :state :queued :progress 0})
    (log-event! :download/queued {:job-id id})
    (.put job-queue id)
    id))

(def page "<!doctype html><meta charset=utf-8>
<form onsubmit=\"fetch('enqueue',{method:'POST',body:u.value});u.value='';return false\">
<input id=u size=60> <button>Add</button></form><pre id=o></pre>
<script>setInterval(async()=>{o.textContent=(await(await fetch('jobs')).json())
.map(x=>`${x.state} ${Math.round(x.progress)}% ${x.url} ${x.state=='failed'?x.last:''}`).join('\\n')},1500)</script>")

(defn valid-url?
  "True when url is an http(s) URL on YouTube or a YouTube subdomain."
  [url]
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
        ;; Read limit+1 bytes: getting them all means the body is over the limit.
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
    ;; With no token configured (local dev), allow all requests.
    (or (nil? token)
        (= auth (str "Bearer " token))
        (some? ingress-path))))

(defn handler
  "Ring handler for the web UI and JSON API."
  [{:keys [uri request-method] :as request}]
  (if (= [:get "/healthz"] [request-method uri])
    {:status 200 :body (json/generate-string {:status "ok"})}
    (if-not (request-authorized? request)
      {:status 401 :body "unauthorized"}
      (case [request-method uri]
        [:get "/"] {:headers {"Content-Type" "text/html"} :body page}
        [:get "/jobs"] {:body (json/generate-string (vec (vals @jobs)))}
        [:post "/enqueue"] (enqueue-request (:body request))
        {:status 404}))))

(defn start-server!
  "Start the HTTP server once, defaulting to port 8099."
  ([] (start-server! 8099))
  ([port]
   (or @server
       (reset! server (srv/run-server handler {:port port})))))

(defn stop-server!
  "Stop the HTTP server if it is running."
  []
  (when-let [stop! @server]
    (stop!)
    (reset! server nil)))

(defn -main [& _]
  (let [port (parse-long (or (System/getenv "PORT") "8099"))]
    (start-server! port)
    (log-event! :server/started {:port port})
    ;; Block the main thread forever; the server runs on its own threads.
    @(promise)))
