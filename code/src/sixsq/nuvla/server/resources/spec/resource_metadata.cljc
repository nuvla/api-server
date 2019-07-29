(ns sixsq.nuvla.server.resources.spec.resource-metadata
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.resource-metadata-action :as action]
    [sixsq.nuvla.server.resources.spec.resource-metadata-attribute :as attribute]
    [sixsq.nuvla.server.resources.spec.resource-metadata-capability :as capability]
    [sixsq.nuvla.server.util.spec :as su]))


(s/def ::type-uri ::core/uri)


(s/def ::required (s/coll-of string? :min-count 1 :type vector?))


(s/def ::resource-metadata
  (su/only-keys-maps common/common-attrs
                     {:req-un [::type-uri]
                      :opt-un [::attribute/attributes
                               ::required
                               ::capability/capabilities
                               ::action/actions]}))
