(ns sixsq.nuvla.server.resources.spec.infrastructure-service-coe-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.infrastructure-service :as service-resource]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-coe :as infrastructure-service-coe]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl
  {:owners   ["group/nuvla-admin"]
   :view-acl ["user/jane"]})


(deftest check-service
  (doseq [coe-type ["swarm" "kubernetes"]]
    (let [timestamp "1964-08-25T10:00:00.00Z"
          service   {:id                    (str service-resource/resource-type "/uuid")
                     :resource-type         service-resource/resource-type
                     :created               timestamp
                     :updated               timestamp
                     :acl                   valid-acl

                     :parent                "infrastructure-service-group/abcdef"

                     :method                "coe"
                     :subtype               coe-type
                     :endpoint              "https://docker.example.org/api"
                     :nodes                 [{:node-name "testmachine" :node-config-base64 "base64==" :manager true}]
                     :management-credential "infrastructure-service/1-2-3-4-5"
                     :state                 "STARTED"
                     :swarm-enabled         true
                     :online                true}]

      (stu/is-valid ::infrastructure-service-coe/schema service)

      ;; mandatory keywords
      (doseq [k #{:id :resource-type :created :updated :acl :parent :method :subtype :state}]
        (stu/is-invalid ::infrastructure-service-coe/schema (dissoc service k)))

      ;;optional keywords
      (doseq [k #{:endpoint :swarm-enabled :online :management-credential :managed :nodes}]
        (stu/is-valid ::infrastructure-service-coe/schema (dissoc service k))))))
