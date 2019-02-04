(ns com.sixsq.slipstream.ssclj.resources.spec.cloud-entry-point
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.common-namespaces :as common-ns]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]
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


(s/def ::cloud-entry-point
  (su/constrained-map keyword? ::cimi-common/resource-link
                      cimi-common/common-attrs
                      {:req-un [::baseURI]}))
