(ns youtube-to-media.core
  (:require [babashka.fs :as fs]
            [babashka.process :refer [sh process]]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [babashka.pods :as pods]
            [clojure.java.io :as io]))
