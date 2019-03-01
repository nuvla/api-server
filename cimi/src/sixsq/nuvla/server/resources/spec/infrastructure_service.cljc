(ns sixsq.nuvla.server.resources.spec.infrastructure-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/name "method"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable false
             :json-schema/templateMutable true

             :json-schema/displayName "method"
             :json-schema/description "service creation method"
             :json-schema/help "service creation method"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::type
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "type"
             :json-schema/name "type"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "service type"
             :json-schema/description "kebab-case identifier for the service type"
             :json-schema/help "kebab-case identifier for the service type"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::endpoint
  (-> (st/spec ::cimi-core/url)
      (assoc :name "endpoint"
             :json-schema/name "endpoint"
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "endpoint"
             :json-schema/description "public API endpoint for the service"
             :json-schema/help "public endpoint from where the service API is accessible"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::state
  (-> (st/spec #{"CREATED",
                 "STARTING", "STARTED",
                 "STOPPING", "STOPPED",
                 "PAUSING", "PAUSED",
                 "SUSPENDING", "SUSPENDED",
                 "ERROR"})
      (assoc :name "state"
             :json-schema/name "state"
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "state"
             :json-schema/description "state of service"
             :json-schema/help "current state of the service"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false

             :json-schema/value-scope {:values  ["CREATED",
                                                 "STARTING", "STARTED",
                                                 "STOPPING", "STOPPED",
                                                 "PAUSING", "PAUSED",
                                                 "SUSPENDING", "SUSPENDED",
                                                 "ERROR"]
                                       :default "CREATED"})))


(s/def ::management-credential-id
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "management-credential-id"
             :json-schema/name "management-credential-id"
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "management credential id"
             :json-schema/description "id of the credential used to manage this service"
             :json-schema/help "id of the credential used to manage this service"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::cimi-common/parent         ;; required for services
                               ::method
                               ::type
                               ::state]
                      :opt-un [::endpoint
                               ::management-credential-id]}))
