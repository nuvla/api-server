(ns sixsq.nuvla.server.resources.spec.deployment
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::module ::cimi-common/resource-link)


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


(def ^:const credential-href-regex #"^credential/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")


(s/def ::api-key
  (-> (st/spec (s/and string? #(re-matches credential-href-regex %)))
      (assoc :name "api-key"
             :json-schema/name "api-key"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "API key"
             :json-schema/description "credential identifier of API key pair"
             :json-schema/help "credential identifier of API key pair"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::api-secret
  (-> (st/spec string?)
      (assoc :name "api-secret"
             :json-schema/name "api-secret"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "API secret"
             :json-schema/description "secret of API key pair"
             :json-schema/help "secret of API key pair"
             :json-schema/group "body"
             :json-schema/order 31
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::api-credentials
  (-> (st/spec (su/only-keys :req-un [::api-key ::api-secret]))
      (assoc :name "api-credentials"
             :json-schema/name "api-credentials"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "map"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/indexed false

             :json-schema/displayName "Nuvla credentials"
             :json-schema/description "Nuvla deployment API credentials"
             :json-schema/help "Nuvla deployment API credentials"
             :json-schema/group "data"
             :json-schema/category "data"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))

(s/def ::credential-id ::cimi-core/nonblank-string)


(def ^:const external-object-id-regex #"^external-object/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn external-object-id? [s] (re-matches external-object-id-regex s))

(s/def ::external-object-id (s/and string? external-object-id?))

(s/def ::external-objects
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


(def ^:const data-record-id-regex #"^data-record/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn data-record-id? [s] (re-matches data-record-id-regex s))
(defn data-record-id-keyword? [s] (-> s symbol str data-record-id?))

(s/def ::data-record-id (s/and string? data-record-id?))
(s/def ::data-record-id-keyword (s/and keyword? data-record-id-keyword?))
(s/def ::data-set-ids (s/nilable (s/coll-of ::data-record-id :min-count 1 :kind vector?)))


(s/def ::data-records
  (-> (st/spec (s/map-of ::data-record-id-keyword ::data-set-ids :min-count 1))
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
                        {:req-un [::module
                                  ::state
                                  ::api-credentials
                                  ::credential-id]
                         :opt-un [::external-objects
                                  ::data-records]}]))

(s/def ::deployment (su/only-keys-maps deployment-keys-spec))
