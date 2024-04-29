(ns sixsq.nuvla.server.resources.spec.nuvlabox
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.nuvlabox.data-utils :as data-utils]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::version
  (-> (st/spec nat-int?)
      (assoc :name "version"
             :json-schema/type "integer"
             :json-schema/description "schema version"

             :json-schema/order 40)))


(s/def ::owner
  (-> (st/spec ::common/id)
      (assoc :name "owner"
             :json-schema/type "resource-id"
             :json-schema/description "id of principal (user or group) that owns the NuvlaBox"

             :json-schema/order 41)))


(def nb-status-href-regex #"^nuvlabox-status/[0-9a-f-]+$")


(s/def ::state-href (s/and string? #(re-matches nb-status-href-regex %)))


(s/def ::nuvlabox-status
  (-> (st/spec ::state-href)
      (assoc :name "nuvlabox-status"
             :json-schema/type "string"
             :json-schema/display-name "NuvlaBox state"
             :json-schema/description "identifier of the associated nuvlabox-status resource"

             :json-schema/order 42)))


(def service-group-href-regex #"^infrastructure-service-group/[0-9a-f-]+$")


(s/def ::isg-href (s/and string? #(re-matches service-group-href-regex %)))


(def credential-href-regex #"^credential/[0-9a-f-]+$")


(s/def ::credential-href (s/and string? #(re-matches credential-href-regex %)))


(s/def ::infrastructure-service-group
  (-> (st/spec ::isg-href)
      (assoc :name "infrastructure-service-group"
             :json-schema/type "string"
             :json-schema/display-name "NuvlaBox infrastructure service group"
             :json-schema/description "identifier of the associated infrastructure-service-group resource"

             :json-schema/order 43)))


(s/def ::credential-api-key
  (-> (st/spec ::credential-href)
      (assoc :name "credential-api-key"
             :json-schema/type "string"
             :json-schema/display-name "NuvlaBox credential api key"
             :json-schema/description "identifier of the associated credential api key resource"

             :json-schema/order 44)))


(s/def ::host-level-management-api-key
  (-> (st/spec ::credential-href)
      (assoc :name "host-level-management-api-key"
             :json-schema/type "string"
             :json-schema/display-name "NuvlaBox credential api key for host level management operations"
             :json-schema/description "when host level management is enabled, it points to the credential api key being used for that purpose"

             :json-schema/order 45)))


(def attributes {:req-un [::version
                          ::owner]
                 :opt-un [::nuvlabox-status
                          ::infrastructure-service-group
                          ::credential-api-key
                          ::host-level-management-api-key]})

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     attributes))

;; actions

(s/def ::dataset (s/coll-of ::core/nonblank-string))
(s/def ::query (s/coll-of ::core/nonblank-string))
(s/def ::filter (st/spec ::core/nonblank-string))
(s/def ::from (st/spec ::core/timestamp))
(s/def ::to (st/spec ::core/timestamp))
(s/def ::granularity (st/spec (s/or :raw #{"raw"}
                                    :granularity-duration data-utils/granularity->duration)))
(s/def ::custom-es-aggregations any?)
(s/def ::bulk-data-body (su/only-keys-maps {:req-un [::from
                                                     ::to]
                                            :opt-un [::dataset
                                                     ::query
                                                     ::filter
                                                     ::granularity
                                                     ::custom-es-aggregations]}))
