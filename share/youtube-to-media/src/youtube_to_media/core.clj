(ns youtube-to-media.core
  (:require [babashka.process :refer [process]]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [org.httpkit.server :as srv]
            [clojure.java.io :as io]))

(def download-directory "./")
(defonce jobs (atom {}))
(defonce worker (agent nil :error-mode :continue))
(defonce server (atom nil))

(defn set-job! [id & kvs]
  (swap! jobs update id #(apply assoc % kvs)))

(defn notify! [job]
  (try
    (http/post "http://supervisor/core/api/events/download_manager_finished"
               {:headers {"Authorization" (str "Bearer " (System/getenv "SUPERVISOR_TOKEN"))}
                :body (json/generate-string (select-keys job [:url :state :last]))})
    (catch Exception _ nil)))

(defn- update-download-line! [id line]
  (if-let [[_ percent] (re-find #"(\\d+(?:\\.\\d+)?)%" line)]
    (set-job! id :progress (parse-double percent))
    (set-job! id :last line)))

(defn- stream-download-output! [id output]
  (with-open [reader (io/reader output)]
    (doseq [line (line-seq reader)]
      (update-download-line! id line))))

(defn download! [id]
  (set-job! id :state :downloading)
  (let [process (process {:out :stream :err :out}
                         "uvx" "yt-dlp" "--no-continue" "--newline"
                         "-P" download-directory "--" (:url (@jobs id)))]
    (stream-download-output! id (:out process))
    (set-job! id :state (if (zero? (:exit @process)) :done :failed))
    (notify! (@jobs id))))

(defn- run-job! [id]
  (try
    (download! id)
    (catch Exception error
      (set-job! id :state :failed :last (ex-message error)))))

(defn enqueue! [url]
  (let [id (str (random-uuid))]
    (swap! jobs assoc id {:url url :state :queued :progress 0})
    (send-off worker (fn [_] (run-job! id)))
    id))

(def page "<!doctype html><meta charset=utf-8>
<form onsubmit=\"fetch('enqueue',{method:'POST',body:u.value});u.value='';return false\">
<input id=u size=60> <button>Add</button></form><pre id=o></pre>
<script>setInterval(async()=>{o.textContent=(await(await fetch('jobs')).json())
.map(x=>`${x.state} ${Math.round(x.progress)}% ${x.url} ${x.state=='failed'?x.last:''}`).join('\\n')},1500)</script>")

(defn- enqueue-request [body]
  (let [url (slurp body)]
    (if (re-find #"^https?://" url)
      {:status 202 :body (enqueue! url)}
      {:status 400 :body "invalid url"})))

(defn handler [{:keys [uri request-method body]}]
  (case [request-method uri]
    [:get "/"] {:headers {"Content-Type" "text/html"} :body page}
    [:get "/jobs"] {:body (json/generate-string (vals @jobs))}
    [:post "/enqueue"] (enqueue-request body)
    {:status 404}))

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
    (println (str "HTTP server listening on port " port))))
