(ns sixsq.nuvla.server.resources.spec.deployment
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
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
                 "PENDING",
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
             :json-schema/order 21)))


(s/def ::api-secret
  (-> (st/spec string?)
      (assoc :name "api-secret"
             :json-schema/type "string"

             :json-schema/display-name "API secret"
             :json-schema/description "secret of API key pair"
             :json-schema/order 22
             :json-schema/sensitive true)))


(s/def ::api-credentials
  (-> (st/spec (su/only-keys :req-un [::api-key ::api-secret]))
      (assoc :name "api-credentials"
             :json-schema/type "map"
             :json-schema/indexed false

             :json-schema/display-name "Nuvla credentials"
             :json-schema/description "Nuvla deployment API credentials"
             :json-schema/order 23)))

(s/def ::api-endpoint
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "api-endpoint"
             :json-schema/type "string"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/display-name "Nuvla endpoint"
             :json-schema/description "Nuvla endpoint"
             :json-schema/order 24)))


(def ^:const data-object-id-regex #"^data-object/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn data-object-id? [s] (re-matches data-object-id-regex s))
(s/def ::data-object-id (s/and string? data-object-id?))

(def ^:const data-record-id-regex #"^data-record/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn data-record-id? [s] (re-matches data-record-id-regex s))
(s/def ::data-record-id (s/and string? data-record-id?))

(s/def ::records-ids
  (-> (st/spec (s/coll-of ::data-record-id))
      (assoc :name "records ids"
             :json-schema/type "array"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/display-name "data record ids"
             :json-schema/description "List of data record ids."
             :json-schema/order 25)))

(s/def ::objects-ids
  (-> (st/spec (s/coll-of ::data-object-id))
      (assoc :name "objects ids"
             :json-schema/type "array"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/display-name "data object ids"
             :json-schema/description "List of data object ids."
             :json-schema/order 26)))

(s/def ::filter
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "filter"
             :json-schema/type "string"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/description "CIMI filter."
             :json-schema/group "body"
             :json-schema/order 27)))

(s/def ::data-type
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "data type"
             :json-schema/type "string"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/description "The data collection resource id, e.g. data-record."
             :json-schema/group "body"
             :json-schema/order 28)))

(s/def ::time-start
  (-> (st/spec ::core/timestamp)
      (assoc :name "time start"
             :json-schema/type "date-time"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/description "Start time to apply to the data with the filter."
             :json-schema/group "body"
             :json-schema/order 29)))

(s/def ::time-end
  (-> (st/spec ::core/timestamp)
      (assoc :name "time start"
             :json-schema/type "date-time"
             :json-schema/editable false
             :json-schema/indexed false

             :json-schema/description "End time to apply to the data with the filter."
             :json-schema/group "body"
             :json-schema/order 30)))

(s/def ::data-filters
  (-> (st/spec (su/only-keys :req-un [::filter ::time-start ::time-end ::data-type]))
      (assoc :name "data filters"
             :json-schema/type "map")))
(s/def ::filters
  (-> (st/spec (s/coll-of ::data-filters))
      (assoc :name "filters"
             :json-schema/type "array")))

(s/def ::records
  (-> (st/spec (su/only-keys :opt-un [::records-ids ::filters]))
      (assoc :name "records"
             :json-schema/type "map")))
(s/def ::objects
  (-> (st/spec (su/only-keys :opt-un [::objects-ids ::filters]))
      (assoc :name "objects"
             :json-schema/type "map")))

(s/def ::data
  (-> (st/spec (su/only-keys :opt-un [::records ::objects]))
      (assoc :name "data"
             :json-schema/type "map")))


(s/def ::registries-credentials
  (-> (st/spec (s/coll-of string? :min-count 1 :kind vector?))
      (assoc :name "registries-credentials"
             :json-schema/type "array"
             :json-schema/indexed false

             :json-schema/display-name "registries credentials"
             :json-schema/description "list of used credentials corresponding to private registries. Position is important and empty string value is allowed and mean not set."
             :json-schema/order 32)))

;;
;; deprecated items
;;

(s/def ::data-objects
  (-> (st/spec (s/coll-of ::data-object-id :min-count 1 :kind vector?))
      (assoc :json-schema/type "array"
             :json-schema/description "Deprecated")))

(s/def ::data-records
  (-> (st/spec (s/coll-of ::data-record-id :min-count 1 :kind vector?))
      (assoc :json-schema/type "array"
             :json-schema/description "Deprecated")))

(s/def ::data-records-filter
  (-> (st/spec string?)
      (assoc :json-schema/type "string"
             :json-schema/description "Deprecated")))

(s/def ::owner
  (-> (st/spec ::common/id)
      (assoc :name "owner"
             :json-schema/type "resource-id"
             :json-schema/description "id of principal (user or group) that owns the Deployment")))

(s/def ::infrastructure-service (-> (st/spec ::common/id)
                                    (assoc :name "infrastructure-service"
                                           :json-schema/type "resource-id"
                                           :json-schema/description "reference to parent infrastructure service"

                                           :json-schema/section "meta"
                                           :json-schema/editable false
                                           :json-schema/server-managed true)))


(s/def ::nuvlabox (-> (st/spec ::common/id)
                      (assoc :name "nuvlabox"
                             :json-schema/type "resource-id"
                             :json-schema/description "reference to parent nuvlabox"

                             :json-schema/section "meta"
                             :json-schema/editable false
                             :json-schema/server-managed true)))


(def ^:const subscription-id-regex #"^sub_.+$")

(defn subscription-id? [s] (re-matches subscription-id-regex s))

(s/def ::subscription-id
  (-> (st/spec (s/and string? subscription-id?))
      (assoc :name "subscription-id"
             :json-schema/type "string"
             :json-schema/description "identifier of subscription id"
             :json-schema/server-managed true
             :json-schema/editable false)))


(s/def ::coupon
  (-> (st/spec string?)
      (assoc :name "coupon"
             :json-schema/type "string"
             :json-schema/description "coupon code")))


(s/def ::execution-mode
  (-> (st/spec #{"pull" "push" "mixed"})
      (assoc :name "execution-mode"
             :json-schema/type "string"
             :json-schema/description "job execution mode"
             :json-schema/value-scope {:values ["pull" "push" "mixed"]})))


(def deployment-keys-spec
  (su/merge-keys-specs [common/common-attrs
                        {:req-un [::module
                                  ::state
                                  ::api-endpoint]
                         :opt-un [::api-credentials
                                  ::data-objects            ;; deprecated
                                  ::data-records            ;; deprecated
                                  ::data-records-filter     ;; deprecated
                                  ::data
                                  ::registries-credentials
                                  ::owner
                                  ::infrastructure-service
                                  ::nuvlabox
                                  ::subscription-id
                                  ::coupon
                                  ::execution-mode]}]))


(s/def ::deployment (su/only-keys-maps deployment-keys-spec))
