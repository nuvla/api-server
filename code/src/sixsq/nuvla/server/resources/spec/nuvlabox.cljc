(ns sixsq.nuvla.server.resources.spec.nuvlabox
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
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


(s/def ::infrastructure-service-group
  (-> (st/spec ::isg-href)
      (assoc :name "infrastructure-service-group"
             :json-schema/type "string"
             :json-schema/display-name "NuvlaBox infrastructure service group"
             :json-schema/description "identifier of the associated infrastructure-service-group resource"

             :json-schema/order 43)))


(def attributes {:req-un [::version
                          ::owner]
                 :opt-un [::nuvlabox-status
                          ::infrastructure-service-group]})


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     attributes))

