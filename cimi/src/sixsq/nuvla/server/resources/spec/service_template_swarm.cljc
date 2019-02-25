(ns sixsq.nuvla.server.resources.spec.service-template-swarm
  "
This template requires the parameters necessary to create a new Docker Swarm
on a cloud infrastructure.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.service-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::cloud-service
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "cloud-service"
             :json-schema/name "cloud-service"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true
             :json-schema/templateMutable false

             :json-schema/displayName "cloud service"
             :json-schema/description "reference to cloud service"
             :json-schema/help "reference to cloud service where the Docker Swarm will be created"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::service-credential
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "service-credential"
             :json-schema/name "service-credential"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true
             :json-schema/templateMutable false

             :json-schema/displayName "service credential"
             :json-schema/description "reference to service credential"
             :json-schema/help "reference to service credential to use to create Docker Swarm"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden true
             :json-schema/sensitive false)))


(def service-template-keys-spec
  {:req-un [::cloud-service
            ::service-credential]})


;; Defines the contents of the this service-template resource.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     service-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     service-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
