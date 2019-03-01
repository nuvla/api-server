(ns sixsq.nuvla.server.resources.spec.data-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::href
  (-> (st/spec ::cimi-core/resource-href)
      (assoc :name "href"
             :json-schema/name "href"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/templateMutable true

             :json-schema/displayName "href"
             :json-schema/description "reference to infrastructure-service resource"
             :json-schema/help "reference to infrastructure-service resource"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::infrastructure-service
  (-> (st/spec (su/only-keys :req-un [::href]))
      (assoc :name "infrastructure-service"
             :json-schema/name "infrastructure-service"
             :json-schema/type "map"
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
