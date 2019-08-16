(ns sixsq.nuvla.server.resources.spec.deployment
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential :as cred-spec]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::parent (-> cred-spec/credential-id-spec
                    (assoc :name "parent"
                           :json-schema/type "resource-id"
                           :json-schema/description "reference to parent credential resource"

                           :json-schema/section "meta"
                           :json-schema/editable false
                           :json-schema/order 6)))


(s/def ::module ::core/resource-link)


(s/def ::state
  (-> (st/spec #{"CREATED",
                 "STARTING", "STARTED",
                 "STOPPING", "STOPPED",
                 "PAUSING", "PAUSED",
                 "SUSPENDING", "SUSPENDED",
                 "UPDATING", "UPDATED",
                 "ERROR"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "state of deployment"
             :json-schema/order 20

             :json-schema/value-scope {:values  ["CREATED",
                                                 "STARTING", "STARTED",
                                                 "STOPPING", "STOPPED",
                                                 "PAUSING", "PAUSED",
                                                 "SUSPENDING", "SUSPENDED",
                                                 "UPDATING", "UPDATED",
                                                 "ERROR"]
                                       :default "CREATED"})))


(def ^:const credential-href-regex #"^credential/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")


(s/def ::api-key
  (-> (st/spec (s/and string? #(re-matches credential-href-regex %)))
      (assoc :name "api-key"
             :json-schema/type "string"

             :json-schema/display-name "API key"
             :json-schema/description "credential identifier of API key pair"
             :json-schema/order 30)))


(s/def ::api-secret
  (-> (st/spec string?)
      (assoc :name "api-secret"
             :json-schema/type "string"

             :json-schema/display-name "API secret"
             :json-schema/description "secret of API key pair"
             :json-schema/order 31
             :json-schema/sensitive true)))


(s/def ::api-credentials
  (-> (st/spec (su/only-keys :req-un [::api-key ::api-secret]))
      (assoc :name "api-credentials"
             :json-schema/type "map"
             :json-schema/indexed false

             :json-schema/display-name "Nuvla credentials"
             :json-schema/description "Nuvla deployment API credentials"
             :json-schema/order 20)))

(s/def ::api-endpoint
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "api-endpoint"
             :json-schema/type "string"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/display-name "Nuvla endpoint"
             :json-schema/description "Nuvla endpoint"
             :json-schema/order 22)))


(def ^:const data-object-id-regex #"^data-object/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn data-object-id? [s] (re-matches data-object-id-regex s))

(s/def ::data-object-id (s/and string? data-object-id?))

(s/def ::data-objects
  (-> (st/spec (s/coll-of ::data-object-id :min-count 1 :kind vector?))
      (assoc :name "data-objects"
             :json-schema/type "array"
             :json-schema/indexed false

             :json-schema/display-name "data objects"
             :json-schema/description "list of data object identifiers"
             :json-schema/order 30)))


(def ^:const data-record-id-regex #"^data-record/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn data-record-id? [s] (re-matches data-record-id-regex s))
(defn data-record-id-keyword? [s] (-> s symbol str data-record-id?))

(s/def ::data-record-id (s/and string? data-record-id?))
(s/def ::data-record-id-keyword (s/and keyword? data-record-id-keyword?))

(def ^:const data-set-id-regex #"^data-set/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn data-set-id? [s] (re-matches data-set-id-regex s))
(s/def ::data-set-id (s/and string? data-set-id?))

(s/def ::data-set-ids (s/nilable (s/coll-of ::data-set-id :min-count 1 :kind vector?)))


(s/def ::data-records
  (-> (st/spec (s/map-of ::data-record-id-keyword ::data-set-ids :min-count 1))
      (assoc :name "serviceOffers"
             :json-schema/type "map"
             :json-schema/indexed false

             :json-schema/display-name "service offers"
             :json-schema/description "data"
             :json-schema/order 31)))


(def deployment-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::module
                                  ::state
                                  ::api-endpoint]
                         :opt-un [::api-credentials
                                  ::data-objects
                                  ::data-records]}]))


(s/def ::deployment (su/only-keys-maps deployment-keys-spec))
