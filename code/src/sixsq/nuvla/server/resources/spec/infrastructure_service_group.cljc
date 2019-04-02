(ns sixsq.nuvla.server.resources.spec.infrastructure-service-group
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::documentation
  (-> (st/spec ::cimi-core/url)
      (assoc :name "documentation"
             :json-schema/name "documentation"
             :json-schema/provider-mandatory false
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "documentation URL"
             :json-schema/description "URL where service documentation can be found"
             :json-schema/help "URL where service documentation can be found"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::infrastructure-services
  (-> (st/spec (s/coll-of ::cimi-common/resource-link :kind vector?))
      (assoc :name "infrastructure-services"
             :json-schema/name "infrastructure-services"
             :json-schema/type "Array"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable false
             :json-schema/indexed false

             :json-schema/display-name "infrastructure-services"
             :json-schema/description "list of associated services"
             :json-schema/help "list of associated service references"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:opt-un [::documentation
                               ::infrastructure-services]}))
