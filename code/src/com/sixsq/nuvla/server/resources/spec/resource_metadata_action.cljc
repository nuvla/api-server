(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-action
  "schema definitions for the 'actions' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-value-scope :as value-scope]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::name ::core/token)


(s/def ::uri ::core/uri)


(s/def ::description ::core/nonblank-string)


;; only those methods typically used in REST APIs are permitted by this implementation
(s/def ::method #{"GET" "POST" "PUT" "DELETE"})


(s/def ::input-message ::core/mimetype)


(s/def ::output-message ::core/mimetype)


(s/def ::type #{"boolean" "long" "integer" "number"
                "double" "string" "map" "date-time" "geo-point"})


(s/def ::parameter (su/only-keys :req-un [::name
                                          ::type]
                                 :opt-un [::value-scope/value-scope
                                          ::child-types
                                          ::description]))


(s/def ::child-types (-> (st/spec (s/coll-of ::parameter :min-count 1 :type vector?))
                         (assoc
                           :json-schema/type "map"
                           :json-schema/indexed false)))


(s/def ::input-parameters
  (s/coll-of ::parameter :min-count 1 :type vector?))


(s/def ::action (su/only-keys :req-un [::name
                                       ::uri
                                       ::method
                                       ::input-message
                                       ::output-message]
                              :opt-un [::description
                                       ::input-parameters]))


;; Ideally, keys within this collection should not be indexed. However,
;; when wrapping this with st/spec, an exception is thrown when evaluating
;; the spec. Use clojure spec directly to work around this problem.
(s/def ::actions
  (s/spec (s/coll-of ::action :min-count 1 :type vector?)))
