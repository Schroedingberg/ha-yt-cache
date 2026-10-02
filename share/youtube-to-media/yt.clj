(ns yt
  (:require [babashka.process :refer [process]]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [org.httpkit.server :as srv]
            [clojure.java.io :as io]))


(def download-directory "./")
(def jobs   (atom {}))                      ; id -> {:url :state :progress :last}
(def worker (agent nil :error-mode :continue)) ; one action at a time = the queue

(defn set-job! [id & kvs] (swap! jobs update id #(apply assoc % kvs)))

(defn notify! [job]                         ; HA event; user wires an automation to it
  (try (http/post "http://supervisor/core/api/events/download_manager_finished"
                  {:headers {"Authorization" (str "Bearer " (System/getenv "SUPERVISOR_TOKEN"))}
                   :body (json/generate-string (select-keys job [:url :state :last]))})
       (catch Exception _)))

(defn download! [id]
  (set-job! id :state :downloading)
  (let [p (process {:out :stream :err :out}  ; merged: one reader, no blocked pipe
                   "uvx" "yt-dlp" "--no-continue" "--newline" "-P" download-directory
                   "--" (:url (@jobs id)))]
    (with-open [r (io/reader (:out p))]
      (doseq [line (line-seq r)]
        (if-let [[_ pct] (re-find #"(\d+(?:\.\d+)?)%" line)]
          (set-job! id :progress (parse-double pct))
          (set-job! id :last line))))
    (set-job! id :state (if (zero? (:exit @p)) :done :failed))
    (notify! (@jobs id))))




(defn enqueue! [url]
  (let [id (str (random-uuid))]
    (swap! jobs assoc id {:url url :state :queued :progress 0})
    (send-off worker (fn [_]
                       (try (download! id)
                            (catch Exception e (set-job! id :state :failed :last (ex-message e))))
                       nil))
    id))

(def page "<!doctype html><meta charset=utf-8>
<form onsubmit=\"fetch('enqueue',{method:'POST',body:u.value});u.value='';return false\">
<input id=u size=60> <button>Add</button></form><pre id=o></pre>
<script>setInterval(async()=>{o.textContent=(await(await fetch('jobs')).json())
.map(x=>`${x.state} ${Math.round(x.progress)}% ${x.url} ${x.state=='failed'?x.last:''}`).join('\\n')},1500)</script>")

(defn handler [{:keys [uri request-method body]}]
  (case [request-method uri]
    [:get "/"]     {:headers {"Content-Type" "text/html"} :body page}
    [:get "/jobs"] {:body (json/generate-string (vals @jobs))}
    [:post "/enqueue"] (let [url (slurp body)]
                         (if (re-find #"^https?://" url)
                           {:status 202 :body (enqueue! url)}
                           {:status 400 :body "invalid url"}))
    {:status 404}))

(comment

  ;; start / stop the server by hand
  (def server (srv/run-server handler {:port 8099}))
  (server)                                   ; calling the returned fn stops it

  ;; enqueue a job and look at it
  (def id (enqueue! "https://www.youtube.com/watch?v=jKeutbXLJ38&list=RDjKeutbXLJ38" ))
  (@jobs id)
  @jobs

  ;; watch progress
  (->> @jobs vals (map (juxt :state :progress)))

  ;; run a download synchronously, bypassing the agent queue
  (swap! jobs assoc "x" {:url "https://www.youtube.com/watch?v=T4P7DDxnMTo"
                         :state :queued :progress 0})
  (download! "x")


  ;; test the notify call (fails harmlessly outside HA)
  (notify! {:url "u" :state :done :last "ok"})

  ;; inspect the agent
  (agent-error worker)                       ; nil when healthy
  (await worker)                             ; block until the queue drains

  ;; reset
  (reset! jobs {})

  ())
