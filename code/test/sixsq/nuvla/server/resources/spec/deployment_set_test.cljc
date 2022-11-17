(ns sixsq.nuvla.server.resources.spec.deployment-set-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.deployment-set :as deployment-set-resource]
    [sixsq.nuvla.server.resources.spec.deployment-set :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})

(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-deployment-set
  {:id            (str deployment-set-resource/resource-type "/uuid")
   :resource-type deployment-set-resource/resource-type
   :created       timestamp
   :updated       timestamp
   :acl           valid-acl

   :state         "CREATED"

   :spec          {:targets      ["credential/a2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
                                  "credential/b2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]
                   :applications ["module/c2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
                                  "module/d2dc1733-ac2c-45b1-b68a-0ec02653bc0c_10"]
                   :env          [{:name        "a"
                                   :value       "a value"
                                   :application "module/d2dc1733-ac2c-45b1-b68a-0ec02653bc0c_10"}]
                   :coupons      [{:code        "a"
                                   :application "module/d2dc1733-ac2c-45b1-b68a-0ec02653bc0c_10"}]
                   }
   :job           "job/e2dc1733-ac2c-45b1-b68a-0ec02653bc0c"})


(deftest test-schema-check
  (stu/is-valid ::t/deployment-set valid-deployment-set)
  (stu/is-valid ::t/deployment-set (assoc-in valid-deployment-set [:spec :start] true))
  (stu/is-invalid ::t/deployment-set (assoc valid-deployment-set :badKey "badValue"))
  (stu/is-invalid ::t/deployment-set (assoc valid-deployment-set :state "wrong"))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:spec :applications] ["must-be-href"]))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:spec :applications] []))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:spec :targets] []))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:spec :env 0] {}))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:spec :env 0] {:name  "b"
                                                                                    :value "b"}))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:spec :coupon 0] {}))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:spec :coupon 0] {:code  "x"}))

  ;; required attributes
  (doseq [k #{:id :resource-type :created :updated :acl :state :spec}]
    (stu/is-invalid ::t/deployment-set (dissoc valid-deployment-set k)))

  ;; optional attributes
  (doseq [k #{:job :env :coupons}]
    (stu/is-valid ::t/deployment-set (dissoc valid-deployment-set k))))
