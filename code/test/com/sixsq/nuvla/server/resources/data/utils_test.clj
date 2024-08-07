(ns com.sixsq.nuvla.server.resources.data.utils-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.resources.data.utils :as u])
  (:import
    (com.amazonaws AmazonServiceException)))


(deftest test-generate-url
  (let [os-host        "s3.cloud.com"
        obj-store-conf {:endpoint (str "https://" os-host)
                        :key      "key"
                        :secret   "secret"}
        bucket         "bucket"
        obj-name       "object/name"
        verb           :put]
    (is (str/starts-with? (u/generate-url obj-store-conf bucket obj-name verb)
                          (format "https://%s/%s/%s?" os-host bucket obj-name)))))


(deftest add-size-or-md5sum

  (with-redefs [u/get-s3-client (fn [_] nil)]

    (with-redefs [u/s3-object-metadata (fn [_ _ _] {:contentLength 1, :contentMD5 "aaa"})]
      (is (= {:bytes 1} (u/add-s3-bytes {})))
      (is (= {:bytes 1} (u/add-s3-bytes nil)))
      (is (= {:bytes 1} (u/add-s3-bytes {:bytes 99})))
      (is (= {:md5sum "aaa"} (u/add-s3-md5sum {})))
      (is (= {:myKey "myvalue" :bytes 1 :md5sum "aaa"} (-> {:myKey "myvalue"}
                                                           (u/add-s3-bytes)
                                                           (u/add-s3-md5sum)))))

    (with-redefs [u/s3-object-metadata (fn [_ _ _] {:contentLength 2, :contentMD5 nil})]
      (is (= {:bytes 2} (u/add-s3-bytes {})))
      (is (= {:bytes 2} (u/add-s3-bytes nil)))
      (is (= {:bytes 2} (u/add-s3-bytes {:bytes 99})))
      (is (= {} (u/add-s3-md5sum {})))
      (is (= {:myKey "myvalue" :bytes 2} (-> {:myKey "myvalue"}
                                             (u/add-s3-bytes)
                                             (u/add-s3-md5sum)))))

    (with-redefs [u/s3-object-metadata (fn [_ _ _] {:contentLength nil, :contentMD5 nil})]
      (is (= {} (u/add-s3-bytes {})))
      (is (= nil (u/add-s3-bytes nil)))
      (is (= {:bytes 99} (u/add-s3-bytes {:bytes 99})))
      (is (= {} (u/add-s3-md5sum {})))
      (is (= {:myKey "myvalue"} (-> {:myKey "myvalue"}
                                    (u/add-s3-bytes)
                                    (u/add-s3-md5sum)))))

    (with-redefs [u/s3-object-metadata (fn [_ _ _] nil)]
      (is (= {} (u/add-s3-bytes {})))
      (is (= nil (u/add-s3-bytes nil)))
      (is (= {:bytes 99} (u/add-s3-bytes {:bytes 99})))
      (is (= {} (u/add-s3-md5sum {})))
      (is (= {:myKey "myvalue"} (-> {:myKey "myvalue"}
                                    (u/add-s3-bytes)
                                    (u/add-s3-md5sum)))))


    (with-redefs [u/s3-object-metadata (fn [_ _ _] (let [ex (doto
                                                              (AmazonServiceException. "Simulated AWS Exception for S3 permission error")
                                                              (.setStatusCode 403))]
                                                     (throw ex)))]
      (is (= {} (u/add-s3-bytes {})))
      (is (= nil (u/add-s3-bytes nil)))
      (is (= {:bytes 99} (u/add-s3-bytes {:bytes 99})))
      (is (= {} (u/add-s3-md5sum {})))
      (is (= {:myKey "myvalue"} (-> {:myKey "myvalue"}
                                    (u/add-s3-bytes)
                                    (u/add-s3-md5sum)))))))
