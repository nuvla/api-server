(ns sixsq.nuvla.server.resources.spec.deployment-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.deployment :as d]
    [sixsq.nuvla.server.resources.spec.deployment :as ds]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})

(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-deployment
  {:id                     (str d/resource-type "/connector-uuid")
   :resource-type          d/resource-type
   :parent                 "credential/d2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
   :created                timestamp
   :updated                timestamp
   :acl                    valid-acl

   :state                  "STARTED"

   :api-credentials        {:api-key    "credential/uuid"
                            :api-secret "api secret"}
   :api-endpoint           "http://blah.example.com"

   :module                 {:href "module-component/my-module-component-uuid"}

   :data {:records {:records-ids ["data-record/1" "data-record/2"]
                    :filters [{:filter "data records filter"
                               :time-start "2020-02-03T23:00:00Z"
                               :time-end "2020-02-03T23:01:00Z"
                               :data-type "data-record"}]}
          :objects {:objects-ids ["data-object/1" "data-object/2"]
                    :filters [{:filter "data objects filter"
                               :time-start "2020-02-03T23:00:00Z"
                               :time-end "2020-02-03T23:01:00Z"
                               :data-type "data-object"}]}}
   :registries-credentials ["credential/uuid1" "credential/uuid2"]})


(deftest test-schema-check
  (stu/is-valid ::ds/deployment valid-deployment)
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :badKey "badValue"))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :module "must-be-href"))

  (stu/is-invalid ::ds/deployment (assoc valid-deployment :data {"BAD_ID" nil}))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :data {:records []}))
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :data {:objects {:objects-ids ["data-records/1"]}}))
  ;; empty filter
  (stu/is-invalid ::ds/deployment (assoc valid-deployment :data {:records {:filters [{:filter ""
                                                                                      :time-start "2020-02-03T23:00:00Z"
                                                                                      :time-end "2020-02-03T23:00:00Z"
                                                                                      :data-type "data-record"}]}}))

  ;; required attributes
  (doseq [k #{:id :resource-type :created :updated :acl :state :module :api-endpoint}]
    (stu/is-invalid ::ds/deployment (dissoc valid-deployment k)))

  ;; optional attributes
  (doseq [k #{:data :api-credentials :credential-id :registries-credentials}]
    (stu/is-valid ::ds/deployment (dissoc valid-deployment k))))
