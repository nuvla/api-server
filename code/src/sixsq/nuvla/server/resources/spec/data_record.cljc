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
             :json-schema/type "string"

             :json-schema/display-name "infrastructure-service-id"
             :json-schema/description "id of service associated with this credential")))


(s/def ::infrastructure-service
  (-> (st/spec ::infrastructure-service-id)
      (assoc :name "infrastructure-service"
             :json-schema/type "string"

             :json-schema/display-name "infrastructure service"
             :json-schema/description "reference to infrastructure-service resource"
             :json-schema/order 21)))


(s/def ::schema
  (su/constrained-map keyword? any?
                      c/common-attrs
                      {:req-un [::infrastructure-service]}))
