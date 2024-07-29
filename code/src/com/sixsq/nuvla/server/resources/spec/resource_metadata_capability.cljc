(ns com.sixsq.nuvla.server.resources.spec.resource-metadata-capability
  "schema definitions for the 'capability' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name ::core/token)

(s/def ::uri ::core/uri)

(s/def ::description ::core/nonblank-string)

(s/def ::value any?)


(s/def ::capability (su/only-keys :req-un [::uri
                                           ::value]
                                  :opt-un [::name
                                           ::description]))

(s/def ::capabilities
  (st/spec {:spec                (s/coll-of ::capability :min-count 1 :type vector?)
            :json-schema/indexed false}))
