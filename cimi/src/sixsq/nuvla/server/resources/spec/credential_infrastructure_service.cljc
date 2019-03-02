(ns sixsq.nuvla.server.resources.spec.credential-infrastructure-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const infrastructure-service-id-regex #"^infrastructure-service/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")

(s/def ::service-id
  (-> (st/spec (s/and string? #(re-matches infrastructure-service-id-regex %)))
      (assoc :name "service-id"
             :json-schema/name "service-id"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "service-id"
             :json-schema/description "id of service associated with this credential"
             :json-schema/help "id of service associated with this credential"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::services
  (-> (st/spec (s/coll-of ::service-id :kind vector?))
      (assoc :name "services"
             :json-schema/name "services"
             :json-schema/type "Array"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "services"
             :json-schema/description "ids of services associated with this credential"
             :json-schema/help "ids of services associated with this credential"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def credential-service-keys-spec (su/merge-keys-specs [cred/credential-keys-spec
                                                        {:opt-un [::services]}]))
