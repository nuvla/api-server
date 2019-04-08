(ns sixsq.nuvla.server.resources.spec.resource-metadata-attribute
  "schema definitions for the 'attributes' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope :as value-scope]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def ::name ::cimi-core/token)

(s/def ::type #{"boolean"
                "date-time" "duration"
                "long" "integer" "number" "double"
                "string" "resource-id" "uri"
                "map" "array"
                "any"})

;;
;; information about whether clients can/should specify a key
;;

(s/def ::server-managed boolean?)

(s/def ::required boolean?)

(s/def ::editable boolean?)


;;
;; useful for rendering forms for browser-based clients
;;

(s/def ::display-name ::cimi-core/nonblank-string)

(s/def ::description ::cimi-core/nonblank-string)

(s/def ::section #{"meta" "data" "acl"})

(s/def ::order nat-int?)

(s/def ::hidden boolean?)

(s/def ::sensitive boolean?)


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

                                          ::server-managed
                                          ::required
                                          ::editable

                                          ::display-name
                                          ::description
                                          ::section
                                          ::order
                                          ::hidden
                                          ::sensitive
                                          ::indexed

                                          ::value-scope/value-scope]))


;; Ideally, keys within this collection should not be indexed. However,
;; when wrapping this with st/spec, an exception is thrown when evaluating
;; the spec. Use clojure spec directly to work around this problem.
(s/def ::attributes
  (s/coll-of ::attribute :min-count 1 :type vector?))
