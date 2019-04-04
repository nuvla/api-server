(ns sixsq.nuvla.server.resources.spec.cloud-entry-point
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::base-uri
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "base-uri"
             :json-schema/name "base-uri"
             :json-schema/type "string"
             :json-schema/required true
             :json-schema/editable false

             :json-schema/display-name "base URI"
             :json-schema/description "base URI for relative href values"
             :json-schema/help "base URI for relative href values"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::collections
  (-> (st/spec (s/map-of ::cimi-core/resource-type-keyword
                         ::cimi-common/resource-link
                         :min-count 1))
      (assoc :name "collections"
             :json-schema/name "collections"
             :json-schema/type "map"
             :json-schema/required false
             :json-schema/editable false

             :json-schema/display-name "collections"
             :json-schema/description "collection resources supported by the server"
             :json-schema/help "collection resources supported by the server"
             :json-schema/group "body"
             :json-schema/order 10
             :json-schema/hidden false
             :json-schema/sensitive false

             :json-schema/indexed false)))


(s/def ::resource
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::base-uri]
                      :opt-un [::collections]}))
