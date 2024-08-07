(ns com.sixsq.nuvla.server.resources.spec.configuration-template-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.resources.configuration-template :as ct]
    [com.sixsq.nuvla.server.resources.spec.configuration-template :as cts]
    [com.sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [com.sixsq.nuvla.server.util.spec :as su]))


(s/def ::configuration-template (su/only-keys-maps cts/resource-keys-spec))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})


(deftest test-configuration-template-schema-check
  (let [timestamp "1964-08-25T10:00:00.00Z"
        root      {:id            (str ct/resource-type "/test")
                   :resource-type p/service-context
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl
                   :service       "cloud-software-solution"}]

    (stu/is-valid ::configuration-template root)

    (doseq [k (into #{} (keys (dissoc root :id :resource-type)))]
      (stu/is-invalid ::configuration-template (dissoc root k)))))
