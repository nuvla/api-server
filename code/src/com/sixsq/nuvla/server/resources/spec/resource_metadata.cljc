(ns com.sixsq.nuvla.server.resources.spec.resource-metadata
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-action :as action]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-attribute :as attribute]
    [com.sixsq.nuvla.server.resources.spec.resource-metadata-capability :as capability]
    [com.sixsq.nuvla.server.util.spec :as su]))


(s/def ::type-uri ::core/uri)


(s/def ::required (s/coll-of string? :min-count 1 :type vector?))


(s/def ::resource-metadata
  (su/only-keys-maps common/common-attrs
                     {:req-un [::type-uri]
                      :opt-un [::attribute/attributes
                               ::required
                               ::capability/capabilities
                               ::action/actions]}))
