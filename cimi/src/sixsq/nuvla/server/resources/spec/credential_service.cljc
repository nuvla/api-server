(ns sixsq.nuvla.server.resources.spec.credential-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::service-link
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "service-link"
             :json-schema/name "service-link"
             :json-schema/type "map"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "service-link"
             :json-schema/description "reference to service associated with this credential"
             :json-schema/help "reference to service associated with this credential"
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::services
  (-> (st/spec (s/coll-of ::service-link :min-count 1 :kind vector?))
      (assoc :name "services"
             :json-schema/name "services"
             :json-schema/type "Array"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "services"
             :json-schema/description "references to services associated with this credential"
             :json-schema/help "references to services associated with this credential"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def credential-service-keys-spec (su/merge-keys-specs [cred/credential-keys-spec
                                                        {:req-un [::services]}]))
