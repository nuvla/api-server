(ns sixsq.nuvla.server.resources.spec.resource-metadata-action
  "schema definitions for the 'actions' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name ::core/token)

(s/def ::uri ::core/uri)

(s/def ::description ::core/nonblank-string)

;; only those methods typically used in REST APIs are permitted by this implementation
(s/def ::method #{"GET" "POST" "PUT" "DELETE"})

(s/def ::input-message ::core/mimetype)

(s/def ::output-message ::core/mimetype)

(s/def ::action (su/only-keys :req-un [::name
                                       ::uri
                                       ::method
                                       ::input-message
                                       ::output-message]
                              :opt-un [::description]))

(s/def ::actions
  (st/spec {:spec                (s/coll-of ::action :min-count 1 :type vector?)
            :json-schema/indexed false}))
