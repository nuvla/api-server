(ns com.sixsq.slipstream.ssclj.resources.external-object-test
  (:require
    [clojure.string :as s]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object.utils :as s3])
  (:import (clojure.lang ExceptionInfo)))

(def s3-host "s3.cloud.com")
(def s3-endpoint (str "https://" s3-host))

(def my-cloud-creds {"credential/my-cred" {:key       "key"
                                           :secret    "secret"
                                           :connector {:objectStoreEndpoint s3-endpoint}}})

(def bucketname "bucket-name")
(def runUUID "1-2-3-4-5")
(def filename "component.1.tgz")
(def objectname "object/name")

(deftest test-upload-fn
  (with-redefs [s3/expand-cred (fn [cred-href] (get my-cloud-creds (:href cred-href)))]

    (let [expected-msg (eo/error-msg-bad-state "upload" #{eo/state-new eo/state-uploading} eo/state-ready)]
      (is (thrown-with-msg? ExceptionInfo (re-pattern expected-msg)
                            (eo/upload-fn {:state eo/state-ready} {}))))

    ;; generic external object
    (is (s/starts-with? (eo/upload-fn {:state           eo/state-new
                                       :contentType     "application/tar+gzip"
                                       :bucketName      bucketname
                                       :objectName      objectname
                                       :objectStoreCred {:href "credential/my-cred"}}
                                      {})
                        (format "https://%s.%s/%s?" bucketname s3-host objectname)))

    ;; external object report
    (is (s/starts-with? (eo/upload-fn {:state           eo/state-new
                                       :contentType     "application/tar+gzip"
                                       :bucketName      bucketname
                                       :objectStoreCred {:href "credential/my-cred"}
                                       :runUUID         runUUID
                                       :filename        filename}
                                      {})
                        (format "https://%s.%s/%s/%s?" bucketname s3-host runUUID filename)))))

(deftest test-download-fn
  (with-redefs [s3/expand-cred (fn [cred-href] (get my-cloud-creds (:href cred-href)))]

    (let [expected-msg (eo/error-msg-bad-state "download" #{eo/state-ready} eo/state-new)]
      (is (thrown-with-msg? ExceptionInfo (re-pattern expected-msg)
                            (eo/download-subtype {:state eo/state-new} {}))))

    (is (s/starts-with? (eo/download-subtype {:state           eo/state-ready
                                              :bucketName      bucketname
                                              :objectName      objectname
                                              :objectStoreCred {:href "credential/my-cred"}}
                                             {})
                        (format "https://%s.%s/%s?" bucketname s3-host objectname)))))
