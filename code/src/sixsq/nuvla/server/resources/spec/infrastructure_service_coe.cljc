(ns sixsq.nuvla.server.resources.spec.infrastructure-service-coe
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.infrastructure-service :as infrastructure-service]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::management-credential
  (-> (st/spec ::common/id)
      (assoc :name "management-credential"
             :json-schema/name "management-credential"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true
             :json-schema/templateMutable false

             :json-schema/displayName "management credential ID"
             :json-schema/description "reference to CSP credential to provision COE"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden true
             :json-schema/sensitive false)))

(s/def ::node-name ::core/nonblank-string)
(s/def ::manager (st/spec boolean?))
(s/def ::node-config-base64 ::core/nonblank-string)
(s/def ::kube-config ::core/nonblank-string)
(s/def ::join-tokens
  (-> (st/spec (su/constrained-map keyword? any?))
      (assoc :name "join-tokens"
             :json-schema/name "join-tokens"
             :json-schema/type "map"
             :json-schema/description "COE cluster manager and worker join tokens"

             :json-schema/editable false
             :json-schema/indexed false
             :json-schema/order 26)))

(s/def ::node
  (-> (st/spec (su/only-keys :req-un [::node-name
                                      ::manager
                                      ::node-config-base64]
                             :opt-un [::kube-config
                                      ::join-tokens]))
      (assoc :name "node"
             :json-schema/name "node"
             :json-schema/type "map"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable false
             :json-schema/consumerWritable false

             :json-schema/displayName "node"
             :json-schema/description "node of the COE cluster"
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
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden true
             :json-schema/sensitive false)))


; Free map with CSP specific cluster parameters
(s/def ::cluster-params
       (-> (st/spec (su/constrained-map keyword? any?))
           (assoc :name "cluster-params"
                  :json-schema/name "cluster-params"
                  :json-schema/type "map"
                  :json-schema/description "parameters of COE cluster on CSP"

                  :json-schema/editable true
                  :json-schema/indexed false
                  :json-schema/order 25)))


(s/def ::schema
  (su/only-keys-maps infrastructure-service/infra-service-keys-spec
                     {:opt-un [::management-credential
                               ::cluster-params
                               ::nodes]}))
