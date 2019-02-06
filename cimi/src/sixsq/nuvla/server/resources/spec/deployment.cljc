(ns sixsq.nuvla.server.resources.spec.deployment
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.deployment-template :as deployment-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::state
  (-> (st/spec #{"CREATED",
                 "STARTING", "STARTED",
                 "STOPPING", "STOPPED",
                 "PAUSING", "PAUSED",
                 "SUSPENDING", "SUSPENDED",
                 "ERROR"})
      (assoc :name "state"
             :json-schema/name "state"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "state"
             :json-schema/description "state of deployment"
             :json-schema/help "standard CIMI state of deployment"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false

             :json-schema/value-scope {:values  ["CREATED",
                                                 "STARTING", "STARTED",
                                                 "STOPPING", "STOPPED",
                                                 "PAUSING", "PAUSED",
                                                 "SUSPENDING", "SUSPENDED",
                                                 "ERROR"]
                                       :default "CREATED"})))


(s/def ::module ::cimi-common/resource-link)

(def ^:const credential-href-regex #"^credential/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")


(s/def ::href
  (-> (st/spec (s/and string? #(re-matches credential-href-regex %)))
      (assoc :name "href"
             :json-schema/name "href"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "href"
             :json-schema/description "credential identifier of API key pair"
             :json-schema/help "credential identifier of API key pair"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::secret
  (-> (st/spec string?)
      (assoc :name "secret"
             :json-schema/name "secret"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "secret"
             :json-schema/description "secret of API key pair"
             :json-schema/help "secret of API key pair"
             :json-schema/group "body"
             :json-schema/order 31
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::clientAPIKey
  (-> (st/spec (su/only-keys :req-un [::href ::secret]))
      (assoc :name "clientAPIKey"
             :json-schema/name "clientAPIKey"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "map"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/indexed false

             :json-schema/displayName "client API key"
             :json-schema/description "client API key used to access SlipStream API"
             :json-schema/help "client API key used to access SlipStream API"
             :json-schema/group "data"
             :json-schema/category "data"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::sshPublicKeys
  (-> (st/spec (s/coll-of ::cimi-core/nonblank-string :min-count 1 :kind vector?))
      (assoc :name "sshPublicKeys"
             :json-schema/name "sshPublicKeys"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "Array"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/indexed false

             :json-schema/displayName "SSH Public Keys"
             :json-schema/description "SSH public keys to add to deployment"
             :json-schema/help "SSH public keys to add to deployment"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::deploymentTemplate ::cimi-common/resource-link)


(def ^:const external-object-id-regex #"^external-object/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn external-object-id? [s] (re-matches external-object-id-regex s))

(s/def ::external-object-id (s/and string? external-object-id?))

(s/def ::externalObjects
  (-> (st/spec (s/coll-of ::external-object-id :min-count 1 :kind vector?))
      (assoc :name "externalObjects"
             :json-schema/name "externalObjects"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "Array"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/indexed false

             :json-schema/displayName "external objects"
             :json-schema/description "list of external object identifiers"
             :json-schema/help "list of external object identifiers to make available to deployment"
             :json-schema/group "data"
             :json-schema/category "data"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def ^:const service-offer-id-regex #"^service-offer/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn service-offer-id? [s] (re-matches service-offer-id-regex s))
(defn service-offer-id-keyword? [s] (-> s symbol str service-offer-id?))

(s/def ::service-offer-id (s/and string? service-offer-id?))
(s/def ::service-offer-id-keyword (s/and keyword? service-offer-id-keyword?))
(s/def ::data-set-ids (s/nilable (s/coll-of ::service-offer-id :min-count 1 :kind vector?)))


(s/def ::serviceOffers
  (-> (st/spec (s/map-of ::service-offer-id-keyword ::data-set-ids :min-count 1))
      (assoc :name "serviceOffers"
             :json-schema/name "serviceOffers"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "map"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/indexed false

             :json-schema/displayName "service offers"
             :json-schema/description "data"
             :json-schema/help "list of available resource operations"
             :json-schema/group "data"
             :json-schema/category "data"
             :json-schema/order 31
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def deployment-keys-spec
  (su/merge-keys-specs [cimi-common/common-attrs
                        deployment-template/deployment-template-keys-spec
                        {:req-un [::state
                                  ::clientAPIKey]
                         :opt-un [::deploymentTemplate
                                  ::sshPublicKeys
                                  ::externalObjects
                                  ::serviceOffers]}]))

(s/def ::deployment (su/only-keys-maps deployment-keys-spec))
