(ns sixsq.nuvla.server.resources.spec.credential-service
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::service
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "service"
             :json-schema/name "service"
             :json-schema/type "map"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true

             :json-schema/displayName "service"
             :json-schema/description "reference to service associated with this credential"
             :json-schema/help "reference to service associated with this credential"
             :json-schema/group "body"
             :json-schema/order 30
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def credential-service-keys-spec (su/merge-keys-specs [cred/credential-keys-spec
                                                        {:req-un [::service]}]))
