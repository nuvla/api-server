(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm
  "
This template requires the parameters necessary to create a new Docker Swarm
on a cloud infrastructure.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


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


(s/def ::machine-name ::cimi-core/nonblank-string)
(s/def ::machine-config-base64 ::cimi-core/nonblank-string)

(s/def ::node
  (-> (st/spec (su/only-keys :req-un [::machine-name
                                      ::machine-config-base64]))
      (assoc :name "node"
             :json-schema/name "node"
             :json-schema/type "map"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable false
             :json-schema/consumerWritable false

             :json-schema/displayName "node"
             :json-schema/description "node within the swarm cluster"
             :json-schema/help "node within the swarm cluster"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::nodes
  (-> (st/spec (s/coll-of ::node :kind vector?))
      (assoc :name "nodes"
             :json-schema/name "nodes"
             :json-schema/type "Array"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable false
             :json-schema/consumerWritable false
             :json-schema/templateMutable false

             :json-schema/displayName "nodes"
             :json-schema/description "List of base64 encoded configurations for each Swarm machine"
             :json-schema/help "List of base64 encoded configurations for each Swarm machine"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden true
             :json-schema/sensitive false)))


(def service-template-keys-spec
  {:req-un [::service-credential
            ::nodes]})


;; Defines the contents of the this service-template resource.
(s/def ::schema
  (su/only-keys-maps infra-service-tpl/resource-keys-spec
                     service-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (su/only-keys-maps infra-service-tpl/template-keys-spec
                     service-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps infra-service-tpl/create-keys-spec
                     {:req-un [::template]}))
