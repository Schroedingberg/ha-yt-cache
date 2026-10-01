(ns yt
  (:require [babashka.fs :as fs]
            [babashka.process :refer [sh process]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [babashka.pods :as pods]
            [clojure.pprint :refer [pprint]]))

(pods/load-pod 'org.babashka/go-sqlite3 "0.3.9")
(require '[pod.babashka.go-sqlite3 :as sqlite])



(def db-path "/share/youtube-to-media/downloads.db")

(defn reset-db! []
  (fs/create-dirs "/share/youtube-to-media")
  (fs/delete-if-exists db-path)
  (sqlite/execute! db-path
                   [(slurp "/share/youtube-to-media/schema.sql")]))









(def example-url "https://www.youtube.com/watch?v=0EqSXDwTq6U&pp=ygUOY2hhcmxpZSBiaXQgbWU%3D")



(defn download-video-command [url]
  [{:out :string
    :err :string
    :continue true}
   "uvx" "--from" "yt-dlp[default]" "python" "-m" "yt_dlp"
   "--no-continue"
   url])






(let [child (process {:err :out}
                     "uvx" "--from" "yt-dlp[default]" "python" "-m" "yt_dlp" "--newline" "https://www.youtube.com/watch?v=T4P7DDxnMTo")
      reader (future (with-open [rdr (io/reader (:out child))]
                       (doseq [line (line-seq rdr)]
                         (println line))))
      {:keys [exit]} @child]
  @reader
  {:exit exit})


(defn enqueue-download! [url]

  (sqlite/execute! db-path ["INSERT INTO jobs (url, state) VALUES (?, ?)" url "queued"]))


(comment

  (reset-db!)

  (fs/list-dir "/share/youtube-to-media")

  (sqlite/query db-path ["SELECT * FROM jobs WHERE state = ?" "queued"])

  (sqlite/execute! db-path ["INSERT INTO jobs (url, state) VALUES (?, ?)" "https://www.youtube.com/watch?v=T4P7DDxnMTo" "queued"])

  (enqueue-download! "https://www.youtube.com/watch?v=T4P7DDxnMTo")

  ())