(ns sixsq.nuvla.server.resources.spec.subscription-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.subscription-config :as t]
    [sixsq.nuvla.server.resources.spec.subscription-config-test :as sct]
    [sixsq.nuvla.server.resources.spec.subscription :as subs]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))

(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})

(def timestamp "1972-10-08T10:00:00.00Z")

(def valid-subs
  (assoc sct/valid-subs-config :resource-id "nuvlabox-state/01"
                               :parent "subscription-config/01"))

(def attrs (conj sct/attrs :resource-id :parent))

(deftest check-subs-schema
  (stu/is-valid ::subs/schema valid-subs)

  (doseq [attr attrs]
    (stu/is-invalid ::subs/schema (dissoc valid-subs attr)))

  (doseq [attr #{:schedule}]
    (stu/is-valid ::subs/schema (dissoc valid-subs attr))))
