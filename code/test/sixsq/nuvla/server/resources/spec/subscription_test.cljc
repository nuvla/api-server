(ns sixsq.nuvla.server.resources.spec.subscription-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.resources.spec.subscription :as subs]
    [sixsq.nuvla.server.resources.spec.subscription-config-test :as sct]
    [sixsq.nuvla.server.resources.subscription-config :as t]))

(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})

(def timestamp "1972-10-08T10:00:00.00Z")

(def valid-subs
  {:id              (str t/resource-type "/01")
   :resource-type   t/resource-type
   :created         timestamp
   :updated         timestamp
   :acl             valid-acl

   :enabled         true
   :category        "notification"
   :resource-id     "nuvlabox-state/01"
   :method-id       "notification/01"
   :parent          "subscription-config/01"
   :resource-kind   "nuvlabox-state"
   :resource-filter "tags='foo'"
   :criteria        {:kind      "numeric"
                     :metric    "load"
                     :value     "75"
                     :condition ">"}
   :schedule        {:rule "1m"}})

(def attrs #{:id :resource-type :created :updated :acl
             :enabled :category :resource-id :parent :method-id
             :resource-kind :resource-filter
             :criteria})

(deftest check-subs-schema
  (stu/is-valid ::subs/schema valid-subs)

  (doseq [attr attrs]
    (stu/is-invalid ::subs/schema (dissoc valid-subs attr)))

  (doseq [attr #{:schedule}]
    (stu/is-valid ::subs/schema (dissoc valid-subs attr))))
