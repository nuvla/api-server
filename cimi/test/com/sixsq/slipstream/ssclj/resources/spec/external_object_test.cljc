(ns com.sixsq.slipstream.ssclj.resources.spec.external-object-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object :as eos]
    [com.sixsq.slipstream.ssclj.resources.spec.spec-test-utils :as stu]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def :cimi.test/external-object (su/only-keys-maps eos/common-external-object-attrs))


(deftest test-schema-check
  (let [root {:state           "new"
              :objectName      "object/name"
              :bucketName      "bucket-name"
              :objectType      "alpha"
              :objectStoreCred {:href "credential/foo"}
              :contentType     "text/html; charset=utf-8"}]

    (stu/is-valid :cimi.test/external-object root)

    ;; mandatory keywords
    (doseq [k #{:state :objectName :bucketName :objectType :objectStoreCred}]
      (stu/is-invalid :cimi.test/external-object (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:href :contentType}]
      (stu/is-valid :cimi.test/external-object (dissoc root k)))))

