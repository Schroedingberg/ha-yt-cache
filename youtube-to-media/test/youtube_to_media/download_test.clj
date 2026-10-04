(ns youtube-to-media.download-test
  (:require [babashka.fs :as fs]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [youtube-to-media.core :as core]))

(use-fixtures :each
  (fn [f]
    (reset! core/jobs {})
    (f)))

(defn- blocking-process [in exit]
  (reify
    clojure.lang.IDeref
    (deref [_] @exit)
    clojure.lang.ILookup
    (valAt [_ k] (when (= k :out) in))
    (valAt [_ k not-found] (if (= k :out) in not-found))))

(deftest download-consumes-output-concurrently-test
  (testing "download! drains large process output without deadlocking"
    (let [id (str (random-uuid))
          in (java.io.PipedInputStream.)
          out (java.io.PipedOutputStream. in)
          exit (promise)]
      (swap! core/jobs assoc id {:url "https://youtube.com/watch?v=abc"})
      ;; The writer emits far more than the pipe's 1024-byte buffer, so it
      ;; blocks until a reader drains the stream. If download! waits for the
      ;; process to exit before reading its output, this deadlocks.
      (future
        (try
          (doseq [i (range 20000)]
            (.write out (.getBytes (str "[download] " (mod i 100) ".0% of 1.00MiB\n"))))
          (.close out)
          (deliver exit {:exit 0})
          (catch java.io.IOException _
            (deliver exit {:exit 1}))))
      (with-redefs [core/process-fn (fn [& _] (blocking-process in exit))
                    core/notify! (fn [_] nil)
                    core/log-event! (fn [& _] nil)
                    core/download-directory (str (fs/create-temp-dir))]
        (let [result (future (core/download! id))]
          (is (not= ::timeout (deref result 3000 ::timeout))
              "download! must consume process output concurrently and complete")
          (is (= :done (:state (get @core/jobs id)))))))))

(defn- exited-process [exit-code out]
  (reify
    clojure.lang.IDeref
    (deref [_] {:exit exit-code})
    clojure.lang.ILookup
    (valAt [_ k] (when (= k :out) out))
    (valAt [_ k not-found] (if (= k :out) out not-found))))

(deftest download-failure-marks-job-failed-test
  (testing "a non-zero exit leaves the job failed with the last output line"
    (let [id (str (random-uuid))
          out (java.io.ByteArrayInputStream. (.getBytes "ERROR: unable to download\n" "UTF-8"))]
      (swap! core/jobs assoc id {:url "https://youtube.com/watch?v=abc"
                                 :state :queued
                                 :progress 0})
      (with-redefs [core/process-fn (fn [& _] (exited-process 1 out))
                    core/notify! (fn [_] nil)
                    core/log-event! (fn [& _] nil)
                    core/download-directory (str (fs/create-temp-dir))]
        (core/download! id)
        (let [job (get @core/jobs id)]
          (is (= :failed (:state job)))
          (is (= "ERROR: unable to download" (:last job))))))))
