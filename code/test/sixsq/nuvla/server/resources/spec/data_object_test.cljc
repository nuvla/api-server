(ns sixsq.nuvla.server.resources.spec.data-object-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.data-object :as eos]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def :cimi.test/data-object (su/only-keys-maps eos/common-data-object-attrs))


(deftest test-schema-check
  (let [root {:state             "new"
              :object-name       "object/name"
              :bucket-name       "bucket-name"
              :object-type       "alpha"
              :object-store-cred {:href "credential/foo"}
              :content-type      "text/html; charset=utf-8"}]

    (stu/is-valid :cimi.test/data-object root)

    ;; mandatory keywords
    (doseq [k #{:state :object-name :bucket-name :object-type :object-store-cred}]
      (stu/is-invalid :cimi.test/data-object (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:href :content-type}]
      (stu/is-valid :cimi.test/data-object (dissoc root k)))))

