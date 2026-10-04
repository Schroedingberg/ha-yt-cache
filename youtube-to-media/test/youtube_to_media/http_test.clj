(ns youtube-to-media.http-test
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [youtube-to-media.core :as core]))

;; Exercise the app through its Ring-style handler: request map in,
;; response map out. This covers routing, auth, enqueue, and the full
;; job state machine end-to-end, without binding a socket (http-kit's
;; server does not run under babashka; the socket layer is http-kit's
;; own code and the container/s6 layer is covered by CI builds).
(defn- request [method uri & {:as opts}]
  (merge {:request-method method :uri uri :headers {}} opts))

(defn- effective-status [resp]
  ;; http-kit defaults a missing :status to 200; mirror that here.
  (or (:status resp) 200))

(defn- poll-until [pred timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond
        (pred) true
        (< deadline (System/currentTimeMillis)) false
        :else (do (Thread/sleep 50) (recur))))))

(defn- job-by-id [id]
  (get @core/jobs id))

(defn- fake-download-process [& _]
  (let [out (java.io.ByteArrayInputStream.
             (.getBytes "[download]   0.0% of 1.00MiB\n[download]  55.5% of 1.00MiB\n[download] 100.0% of 1.00MiB\n" "UTF-8"))]
    (reify
      clojure.lang.IDeref
      (deref [_] {:exit 0})
      clojure.lang.ILookup
      (valAt [_ k] (when (= k :out) out))
      (valAt [_ k not-found] (if (= k :out) out not-found)))))

(use-fixtures :each
  (fn [f]
    (reset! core/jobs {})
    (f)))

(deftest healthz-test
  (let [{:keys [status body]} (core/handler (request :get "/healthz"))]
    (is (= 200 status))
    (is (= "ok" (get (json/parse-string body true) :status)))))

(deftest index-page-test
  (let [resp (core/handler (request :get "/"))]
    (is (= 200 (effective-status resp)))
    (is (str/includes? (or (:body resp) "") "<form"))))

(deftest jobs-empty-test
  (let [resp (core/handler (request :get "/jobs"))]
    (is (= 200 (effective-status resp)))
    (is (= "[]" (:body resp)))))

(deftest enqueue-rejects-invalid-url-test
  (let [{:keys [status]} (core/handler (request :post "/enqueue"
                                                :body (java.io.ByteArrayInputStream. (.getBytes "not a url"))))]
    (is (= 400 status))))

(deftest enqueue-rejects-oversized-body-test
  (let [{:keys [status]} (core/handler (request :post "/enqueue"
                                                :body (java.io.ByteArrayInputStream.
                                                       (.getBytes (apply str (repeat 9000 "a"))))))]
    (is (= 413 status))))

(deftest unknown-route-404-test
  (let [{:keys [status]} (core/handler (request :get "/nope"))]
    (is (= 404 status))))

(deftest enqueue-lifecycle-end-to-end-test
  (testing "a valid URL flows queued -> downloading -> done with progress tracked"
    (with-redefs [core/process-fn fake-download-process
                  core/notify! (fn [_] nil)
                  core/log-event! (fn [& _] nil)
                  core/download-directory (str (fs/create-temp-dir))]
      (let [{:keys [status body]} (core/handler
                                   (request :post "/enqueue"
                                            :body (java.io.ByteArrayInputStream.
                                                   (.getBytes "https://youtube.com/watch?v=abc"))))
            id body]
        (is (= 202 status))
        (is (string? id))
        (is (true? (poll-until #(= :done (:state (job-by-id id))) 5000))
            "job should reach :done within the timeout")
        (let [job (job-by-id id)]
          (is (= :done (:state job)))
          (is (= 100.0 (:progress job))))))))
