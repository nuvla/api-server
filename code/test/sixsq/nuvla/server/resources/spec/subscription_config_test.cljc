(ns sixsq.nuvla.server.resources.spec.subscription-config-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.subscription-config :as t]
    [sixsq.nuvla.server.resources.spec.subscription-config :as subs]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))

(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})

(def timestamp "1972-10-08T10:00:00.00Z")

(def valid-subs-config
  {:id            (str t/resource-type "/01")
   :resource-type t/resource-type
   :created       timestamp
   :updated       timestamp
   :acl           valid-acl

   :enabled true
   :category "notification"
   :method-id "notification/01"
   :resource-kind "nuvlabox-state"
   :resource-filter "tags='foo'"
   :criteria {:kind "numeric"
              :metric "load"
              :value "75"
              :condition ">"}
   :schedule {:rule "1m"}})

(def attrs #{:id :resource-type :created :updated :acl
             :enabled :category :method-id
             :resource-kind :resource-filter
             :criteria})

(deftest check-subs-config-schema
  (stu/is-valid ::subs/schema valid-subs-config)

  (doseq [attr attrs]
    (stu/is-invalid ::subs/schema (dissoc valid-subs-config attr)))

  (doseq [attr #{:schedule}]
    (stu/is-valid ::subs/schema (dissoc valid-subs-config attr))))
