(ns sixsq.nuvla.server.resources.spec.subscription-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.subscription :as subs]
    [sixsq.nuvla.server.resources.spec.subscription-config-test :as sct]))

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
