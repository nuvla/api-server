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
             :json-schema/display-name "documentation URL"
             :json-schema/description "URL where service documentation can be found"

             :json-schema/order 20)))


(s/def ::infrastructure-services
  (-> (st/spec (s/coll-of ::cimi-common/resource-link :kind vector?))
      (assoc :name "infrastructure-services"
             :json-schema/type "array"
             :json-schema/description "list of associated services"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 21
             :json-schema/indexed false)))


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     {:opt-un [::documentation
                               ::infrastructure-services]}))
