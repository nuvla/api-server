(ns sixsq.nuvla.server.resources.spec.resource-metadata-attribute
  "schema definitions for the 'attributes' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope :as value-scope]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name ::cimi-core/token)

(s/def ::type #{"boolean" "dateTime" "duration" "integer" "string" "ref" "double" "URI"
                "map" "Array" "Any"})

(s/def ::provider-mandatory boolean?)

(s/def ::consumer-mandatory boolean?)

(s/def ::mutable boolean?)

(s/def ::consumer-writable boolean?)

(s/def ::template-mutable boolean?)


;;
;; the following attributes are extensions to the standard that are
;; useful for rendering forms for browser-based clients
;;

(s/def ::display-name ::cimi-core/nonblank-string)

(s/def ::description ::cimi-core/nonblank-string)

(s/def ::help ::cimi-core/nonblank-string)

(s/def ::group #{"metadata" "body" "operations" "acl"})

(s/def ::category ::cimi-core/nonblank-string)

(s/def ::order nat-int?)

(s/def ::hidden boolean?)

(s/def ::sensitive boolean?)

(s/def ::lines pos-int?)


;;
;; this attribute helps the interaction with elasticsearch
;; to prevent unwanted indexing of attributes
;;

(s/def ::indexed boolean?)


;;
;; this definition provides a recursive schema for attributes
;; which can have a list of attributes as a child-type element
;;
;; this is useful for attributes that are themselves maps or
;; vectors
;;

(s/def ::attribute nil)

(s/def ::child-types (s/coll-of ::attribute :min-count 1 :type vector?))

(s/def ::attribute (su/only-keys :req-un [::name
                                          ::type]
                                 :opt-un [::child-types

                                          ::provider-mandatory
                                          ::consumer-mandatory
                                          ::mutable
                                          ::consumer-writable

                                          ::display-name
                                          ::description
                                          ::help
                                          ::group
                                          ::category
                                          ::order
                                          ::hidden
                                          ::sensitive
                                          ::lines
                                          ::template-mutable
                                          ::indexed

                                          ::value-scope/value-scope]))


;; Ideally, keys within this collection should not be indexed. However,
;; when wrapping this with st/spec, an exception is thrown when evaluating
;; the spec. Use clojure spec directly to work around this problem.
(s/def ::attributes
  (s/coll-of ::attribute :min-count 1 :type vector?))
