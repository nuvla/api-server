(ns sixsq.nuvla.server.resources.spec.cloud-entry-point
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::base-uri
  (-> (st/spec ::cimi-core/url)
      (assoc :name "base-uri"
             :json-schema/name "base-uri"
             :json-schema/editable false
             :json-schema/display-name "base URI"
             :json-schema/description "base URI for relative href values"

             :json-schema/order 20)))


(s/def ::collections
  (-> (st/spec (s/map-of ::cimi-core/resource-type-keyword
                         ::cimi-common/resource-link
                         :min-count 1))
      (assoc :name "collections"
             :json-schema/name "collections"
             :json-schema/type "map"
             :json-schema/display-name "collections"
             :json-schema/description "collection resources supported by the server"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/indexed false
             :json-schema/order 21)))


(s/def ::resource
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::base-uri]
                      :opt-un [::collections]}))
