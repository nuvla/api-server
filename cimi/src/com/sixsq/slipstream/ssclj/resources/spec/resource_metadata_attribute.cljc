(ns com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-attribute
  "schema definitions for the 'attributes' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name ::cimi-core/token)

(s/def ::namespace ::cimi-core/uri)

(s/def ::uri ::cimi-core/uri)

(s/def ::type #{"boolean" "dateTime" "duration" "integer" "string" "ref" "double" "URI"
                "map" "Array" "Any"})

(s/def ::providerMandatory boolean?)

(s/def ::consumerMandatory boolean?)

(s/def ::mutable boolean?)

(s/def ::consumerWritable boolean?)

(s/def ::templateMutable boolean?)


;;
;; the following attributes are extensions to the standard that are
;; useful for rendering forms for browser-based clients
;;

(s/def ::displayName ::cimi-core/nonblank-string)

(s/def ::description ::cimi-core/nonblank-string)

(s/def ::help ::cimi-core/nonblank-string)

(s/def ::group #{"metadata" "body" "operations" "acl"})

(s/def ::category ::cimi-core/nonblank-string)

(s/def ::order nat-int?)

(s/def ::hidden boolean?)

(s/def ::sensitive boolean?)

(s/def ::lines pos-int?)


;;
;; NOTE: The CIMI specification states that the :type attributes will
;; not be present for standard CIMI attributes.  This implementation
;; makes :type mandatory for all attribute descriptions.  This makes
;; life easier for clients.
;;
(s/def ::attribute (su/only-keys :req-un [::name
                                          ::type
                                          ::providerMandatory
                                          ::consumerMandatory
                                          ::mutable
                                          ::consumerWritable]
                                 :opt-un [::namespace
                                          ::uri
                                          ::displayName
                                          ::description
                                          ::help
                                          ::group
                                          ::category
                                          ::order
                                          ::hidden
                                          ::sensitive
                                          ::lines
                                          ::templateMutable]))

(s/def ::attributes
  (st/spec {:spec                (s/coll-of ::attribute :min-count 1 :type vector?)
            :json-schema/indexed false}))
