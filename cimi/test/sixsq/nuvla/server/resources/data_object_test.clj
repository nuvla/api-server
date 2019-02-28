(ns sixsq.nuvla.server.resources.data-object-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.data-object :as eo]
    [sixsq.nuvla.server.resources.data-object.utils :as s3])
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

    ;; generic data object
    (is (str/starts-with? (eo/upload-fn {:state             eo/state-new
                                         :content-type      "application/tar+gzip"
                                         :bucket-name       bucketname
                                         :object-name       objectname
                                         :object-store-cred {:href "credential/my-cred"}}
                                        {})
                          (format "https://%s/%s/%s?" s3-host bucketname objectname)))

    ;; data object report
    (is (str/starts-with? (eo/upload-fn {:state             eo/state-new
                                         :content-type      "application/tar+gzip"
                                         :bucket-name       bucketname
                                         :object-store-cred {:href "credential/my-cred"}
                                         :runUUID           runUUID
                                         :filename          filename}
                                        {})
                          (format "https://%s/%s/%s/%s?" s3-host bucketname runUUID filename)))))

(deftest test-download-fn
  (with-redefs [s3/expand-cred (fn [cred-href] (get my-cloud-creds (:href cred-href)))]

    (let [expected-msg (eo/error-msg-bad-state "download" #{eo/state-ready} eo/state-new)]
      (is (thrown-with-msg? ExceptionInfo (re-pattern expected-msg)
                            (eo/download-subtype {:state eo/state-new} {}))))

    (is (str/starts-with? (eo/download-subtype {:state             eo/state-ready
                                                :bucket-name       bucketname
                                                :object-name       objectname
                                                :object-store-cred {:href "credential/my-cred"}}
                                               {})
                          (format "https://%s/%s/%s?" s3-host bucketname objectname)))))
