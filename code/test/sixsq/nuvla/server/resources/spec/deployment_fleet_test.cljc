(ns sixsq.nuvla.server.resources.spec.deployment-fleet-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.deployment-fleet :as deployment-fleet-resource]
    [sixsq.nuvla.server.resources.spec.deployment-fleet :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})

(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-deployment-fleet
  {:id            (str deployment-fleet-resource/resource-type "/uuid")
   :resource-type deployment-fleet-resource/resource-type
   :created       timestamp
   :updated       timestamp
   :acl           valid-acl

   :state         "CREATED"

   :spec          {:targets ["credential/a2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
                             "credential/b2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]
                   :applications ["module/c2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
                                  "module/d2dc1733-ac2c-45b1-b68a-0ec02653bc0c_10"]}})


(deftest test-schema-check
  (stu/is-valid ::t/deployment-fleet valid-deployment-fleet)
  (stu/is-invalid ::t/deployment-fleet (assoc valid-deployment-fleet :badKey "badValue"))
  (stu/is-invalid ::t/deployment-fleet (assoc valid-deployment-fleet :state "wrong"))
  (stu/is-invalid ::t/deployment-fleet (assoc-in valid-deployment-fleet [:spec :applications] "must-be-href"))

  ;; required attributes
  (doseq [k #{:id :resource-type :created :updated :acl :state :spec}]
    (stu/is-invalid ::t/deployment-fleet (dissoc valid-deployment-fleet k)))

  ;; optional attributes
  (doseq [k #{}]
    (stu/is-valid ::t/deployment-fleet (dissoc valid-deployment-fleet k))))
