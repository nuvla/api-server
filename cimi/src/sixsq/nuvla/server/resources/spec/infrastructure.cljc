(ns sixsq.nuvla.server.resources.spec.infrastructure
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::services
  (-> (st/spec (s/coll-of ::cimi-common/resource-link :min-count 1 :kind vector?))
      (assoc :name "services"
             :json-schema/name "services"
             :json-schema/type "Array"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true
             :json-schema/indexed false

             :json-schema/displayName "services"
             :json-schema/description "list of associated services"
             :json-schema/help "list of associated service references"
             :json-schema/group "data"
             :json-schema/category "data"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:opt-un [::services]}))
