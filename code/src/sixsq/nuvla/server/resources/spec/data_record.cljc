(ns sixsq.nuvla.server.resources.spec.data-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const infrastructure-service-id-regex #"^infrastructure-service/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")


(s/def ::infrastructure-service-id
  (-> (st/spec (s/and string? #(re-matches infrastructure-service-id-regex %)))
      (assoc :name "infrastructure-service-id"
             :json-schema/name "infrastructure-service-id"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "infrastructure-service-id"
             :json-schema/description "id of service associated with this credential"
             :json-schema/help "id of service associated with this credential"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::infrastructure-service
  (-> (st/spec ::infrastructure-service-id)
      (assoc :name "infrastructure-service"
             :json-schema/name "infrastructure-service"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/templateMutable true

             :json-schema/displayName "infrastructure service"
             :json-schema/description "reference to infrastructure-service resource"
             :json-schema/help "reference to infrastructure-service resource"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::schema
  (su/constrained-map keyword? any?
                      c/common-attrs
                      {:req-un [::infrastructure-service]}))
