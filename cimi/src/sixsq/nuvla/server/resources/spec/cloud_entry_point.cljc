(ns sixsq.nuvla.server.resources.spec.cloud-entry-point
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::baseURI
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "baseURI"
             :json-schema/name "baseURI"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable false

             :json-schema/displayName "base URI"
             :json-schema/description "base URI for relative href values"
             :json-schema/help "base URI for relative href values"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::collections
  (-> (st/spec (s/map-of keyword?                           ;; FIXME: will be ::cimi-core/resource-type-keyword
                         ::cimi-common/resource-link :min-count 1))
      (assoc :name "collections"
             :json-schema/name "collections"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "Array"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory false
             :json-schema/mutable false
             :json-schema/consumerWritable false

             :json-schema/displayName "collections"
             :json-schema/description "collection resources supported by the server"
             :json-schema/help "collection resources supported by the server"
             :json-schema/group "body"
             :json-schema/order 10
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::resource
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::baseURI]
                      :opt-un [::collections]}))
