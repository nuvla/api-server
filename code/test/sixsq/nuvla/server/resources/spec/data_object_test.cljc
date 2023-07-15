(ns sixsq.nuvla.server.resources.spec.data-object-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.data-object :as eos]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def :cimi.test/data-object (su/only-keys-maps eos/common-data-object-attrs))


(deftest test-schema-check
  (let [timestamp "2019-04-15T12:23:53Z"

        location  [6.143158 46.204391 373.0]

        root      {:subtype      "alpha"
                   :state        "NEW"
                   :bucket       "bucket"
                   :object       "object/name"
                   :credential   "credential/d3167d53-0138-4754-b8fd-df8119474e7f"
                   :content-type "text/html; charset=utf-8"
                   :bytes        10234
                   :md5sum       "abcde"
                   :timestamp    timestamp
                   :location     location}]

    (stu/is-valid :cimi.test/data-object root)

    ;; mandatory keywords
    (doseq [k #{:subtype :state :bucket :object :credential}]
      (stu/is-invalid :cimi.test/data-object (dissoc root k)))

    ;; optional keywords
    (doseq [k #{:content-type :bytes :md5sum :timestamp :location}]
      (stu/is-valid :cimi.test/data-object (dissoc root k)))))

