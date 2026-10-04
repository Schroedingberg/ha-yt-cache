(ns youtube-to-media.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [youtube-to-media.core :as core]))

(deftest valid-url?-test
  (testing "accepts YouTube hosts"
    (doseq [url ["https://www.youtube.com/watch?v=kgrV3_g9rYY"
                 "https://youtube.com/watch?v=abc"
                 "https://youtu.be/dQw4w9WgXcQ"
                 "https://music.youtube.com/watch?v=abc"
                 "https://m.youtube.com/watch?v=abc"
                 "http://www.youtube.com/watch?v=abc"]]
      (is (core/valid-url? url) (str "should accept: " url))))
  (testing "rejects non-YouTube or unsafe URLs"
    (doseq [url ["https://example.com/watch?v=abc"
                 "https://youtube.com.evil.com/watch"
                 "https://evilyoutube.com/watch"
                 "https://youtu.be.evil.com/watch"
                 "https://notyoutu.be/watch"
                 "http://127.0.0.1/enqueue"
                 "http://localhost/enqueue"
                 "file:///etc/passwd"
                 "ftp://youtube.com/watch"
                 "javascript:alert(1)"
                 "not a url"
                 ""]]
      (is (not (core/valid-url? url)) (str "should reject: " url)))))
