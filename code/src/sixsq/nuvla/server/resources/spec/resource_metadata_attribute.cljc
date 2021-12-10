(ns sixsq.nuvla.server.resources.spec.resource-metadata-attribute
  "schema definitions for the 'attributes' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.resource-metadata-value-scope :as value-scope]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name ::core/token)

(s/def ::type #{"boolean"
                "date-time" "duration"
                "long" "integer" "number" "double"
                "string" "resource-id" "uri"
                "map" "array" "geo-point" "geo-shape"
                "any"})

;;
;; information about whether clients can/should specify a key
;;

(s/def ::server-managed boolean?)

(s/def ::required (s/coll-of string? :min-count 1 :type vector?))

(s/def ::editable boolean?)


;;
;; useful for rendering forms for browser-based clients
;;

(s/def ::display-name ::core/nonblank-string)

(s/def ::description ::core/nonblank-string)

(s/def ::section #{"meta" "data" "acl"})

(s/def ::order nat-int?)

(s/def ::hidden boolean?)

(s/def ::sensitive boolean?)


;;
;; these attributes help with the interaction with elasticsearch
;;

(s/def ::indexed boolean?)


(s/def ::fulltext boolean?)


;;
;; this definition provides a recursive schema for attributes
;; which can have a list of attributes as a child-type element
;;
;; this is useful for attributes that are themselves maps or
;; vectors
;;

(s/def ::attribute string?)

(s/def ::child-types (-> (st/spec (s/coll-of ::attribute :min-count 1 :type vector?))
                         (assoc
                           :json-schema/type "map"
                           :json-schema/indexed false)))

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
                                          ::fulltext

                                          ::value-scope/value-scope]))


;; Ideally, keys within this collection should not be indexed. However,
;; when wrapping this with st/spec, an exception is thrown when evaluating
;; the spec. Use clojure spec directly to work around this problem.
(s/def ::attributes
  (s/coll-of ::attribute :min-count 1 :type vector?))
