(ns sixsq.nuvla.server.resources.spec.resource-metadata-capability
  "schema definitions for the 'capability' field of a ResourceMetadata resource"
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::name ::cimi-core/token)

(s/def ::uri ::cimi-core/uri)

(s/def ::description ::cimi-core/nonblank-string)

(s/def ::value any?)


(s/def ::capability (su/only-keys :req-un [::uri
                                           ::value]
                                  :opt-un [::name
                                           ::description]))

(s/def ::capabilities
  (st/spec {:spec                (s/coll-of ::capability :min-count 1 :type vector?)
            :json-schema/indexed false}))
