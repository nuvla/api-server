(ns sixsq.nuvla.server.resources.spec.deployment-set-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.deployment-set :as deployment-set-resource]
    [sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [sixsq.nuvla.server.resources.spec.deployment-set :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-anon"]})

(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-deployment-set
  {:id                (str deployment-set-resource/resource-type "/uuid")
   :resource-type     deployment-set-resource/resource-type
   :created           timestamp
   :updated           timestamp
   :acl               valid-acl

   :state             utils/state-new
   :applications-sets [{:id         "module/c2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
                        :version    1
                        :overwrites [{:applications [{:id                      "module/c2dc1733-ac2c-45b1-b68a-0ec02653bc0f"
                                                      :version                 10
                                                      :environmental-variables [{:name  "env_var"
                                                                                 :value "some value"}]}]
                                      :targets      ["credential/a2dc1733-ac2c-45b1-b68a-0ec02653bc0c"
                                                     "credential/b2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]}]}]})


(deftest test-schema-check
  (stu/is-valid ::t/deployment-set valid-deployment-set)
  (stu/is-valid ::t/deployment-set (assoc valid-deployment-set :start true))
  (stu/is-invalid ::t/deployment-set (assoc valid-deployment-set :badKey "badValue"))
  (stu/is-invalid ::t/deployment-set (assoc valid-deployment-set :state "wrong"))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:applications-sets 0 :id] "must-be-href"))
  (stu/is-invalid ::t/deployment-set (assoc valid-deployment-set :applications-sets []))
  (stu/is-invalid ::t/deployment-set (assoc-in valid-deployment-set [:applications-sets 0 :overwrites 0 :targets] []))
  (stu/is-valid ::t/deployment-set (assoc-in valid-deployment-set [:applications-sets 0 :overwrites 0 :applications 0
                                                                   :environmental-variables] []))
  (stu/is-valid ::t/deployment-set (assoc-in valid-deployment-set [:applications-sets 0 :overwrites 0 :applications 0
                                                                   :registries-credentials] ["credential/f2dc1733-ac2c-45b1-b68a-0ec02653bc0c"]))

  ;; required attributes
  (doseq [k #{:id :resource-type :created :updated :acl :state}]
    (stu/is-invalid ::t/deployment-set (dissoc valid-deployment-set k)))

  ;; optional attributes
  #_(doseq [k #{}]
    (stu/is-valid ::t/deployment-set (dissoc valid-deployment-set k))))
