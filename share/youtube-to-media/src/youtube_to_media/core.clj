(ns youtube-to-media.core
  (:require [babashka.fs :as fs]
            [babashka.process :refer [sh process]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [babashka.pods :as pods]
            [clojure.java.io :as io]))



(pods/load-pod 'org.babashka/go-sqlite3 "0.3.9")
(require '[pod.babashka.go-sqlite3 :as sqlite])

