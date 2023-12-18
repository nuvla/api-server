(ns sixsq.nuvla.server.resources.spec.deployment-set-operational-status-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.spec.deployment-set-operational-status :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))

(def deployment-id-1 "deployment/id-1")
(def deployment-id-2 "deployment/id-2")
(def deployment-id-3 "deployment/id-3")

(def app-id "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8")

(def app-env-vars [{:name  "var_1_value"
                    :value "overwritten var1 overwritten in deployment set"}
                   {:name  "var_2"
                    :value "overwritten in deployment set"}])
(def target-id "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316")
(def current-deployment
  {:id          deployment-id-3
   :app-set     "set-1"
   :application {:id                      app-id
                 :version                 1
                 :environmental-variables app-env-vars}
   :target      target-id
   :state       "STARTED"})
(def target-deployment
  {:app-set     "set-1"
   :application {:id                      app-id
                 :version                 1
                 :environmental-variables app-env-vars}
   :target      target-id})

(def valid-operational-status
  {:deployments-to-add    [target-deployment]
   :deployments-to-remove [deployment-id-2]
   :deployments-to-update [[current-deployment target-deployment]]
   :missing-edges         ["nuvlabox/1"]
   :status                "OK"})

(deftest test-schema-check
  (stu/is-valid ::t/operational-status valid-operational-status)
  (stu/is-invalid ::t/operational-status (assoc valid-operational-status :badKey "badValue"))
  (stu/is-invalid ::t/operational-status (assoc valid-operational-status :status "wrong"))

  ;; required attributes
  (doseq [k #{:status}]
    (stu/is-invalid ::t/operational-status (dissoc valid-operational-status k)))

  ;; optional attributes
  (doseq [k #{:deployments-to-add :deployments-to-remove :deployments-to-update}]
    (stu/is-valid ::t/operational-status (dissoc valid-operational-status k))))
