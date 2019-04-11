(ns sixsq.nuvla.server.resources.spec.data-object-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.data-object :as eos]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def :cimi.test/data-object (su/only-keys-maps eos/common-data-object-attrs))


(deftest test-schema-check
  (let [root {:type         "alpha"
              :state        "NEW"
              :bucket       "bucket"
              :object       "object/name"
              :credential   "credential/foo"
              :content-type "text/html; charset=utf-8"
              :bytes        10234
              :md5sum       "abcde"}]

    (stu/is-valid :cimi.test/data-object root)

    ;; mandatory keywords
    (doseq [k #{:type :state :bucket :object :credential}]
      (stu/is-invalid :cimi.test/data-object (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:content-type :bytes :md5sum}]
      (stu/is-valid :cimi.test/data-object (dissoc root k)))))

