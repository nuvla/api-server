(ns com.sixsq.nuvla.server.middleware.gzip-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.middleware.gzip :as t]
    [peridot.request :as pr]))


(defn request
  [headers]
  (pr/build "/"  nil headers nil nil))

(deftest detect-request-gzipped
  (is (nil? (t/content-encoding (request {}))))
  (is (= (t/content-encoding (request {:content-encoding "gzip"})) "gzip"))
  (is (= (t/content-encoding (request {:Content-Encoding "gzip"})) "gzip")))

(deftest test-gzip?
  (is (false?(t/gzip? (request {}))))
  (is (t/gzip? (request {:content-encoding "gzip"})))
  (is (t/gzip? (request {:Content-Encoding "gzip"}))))

