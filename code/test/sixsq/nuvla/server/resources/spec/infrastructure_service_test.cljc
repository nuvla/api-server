(ns sixsq.nuvla.server.resources.spec.infrastructure-service-test
  (:require
    [clojure.test :refer [deftest]]
    [sixsq.nuvla.server.resources.infrastructure-service :as service-resource]
    [sixsq.nuvla.server.resources.spec.infrastructure-service :as infrastructure-service]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["user/jane"]})


(deftest check-service
  (let [timestamp "1964-08-25T10:00:00.00Z"
        service   {:id            (str service-resource/resource-type "/uuid")
                   :resource-type service-resource/resource-type
                   :created       timestamp
                   :updated       timestamp
                   :acl           valid-acl

                   :parent        "infrastructure-service-group/abcdef"

                   :method        "generic"
                   :subtype       "docker"
                   :endpoint      "https://docker.example.org/api"
                   :state         "STARTED"
                   :swarm-enabled true
                   :online        true}]

    (stu/is-valid ::infrastructure-service/schema service)

    ;; mandatory keywords
    (doseq [k #{:id :resource-type :created :updated :acl :method :subtype :endpoint}]
      (stu/is-invalid ::infrastructure-service/schema (dissoc service k)))

    ;;optional keywords
    (doseq [k #{ :swarm-enabled :online :parent :state}]
      (stu/is-valid ::infrastructure-service/schema (dissoc service k)))))
