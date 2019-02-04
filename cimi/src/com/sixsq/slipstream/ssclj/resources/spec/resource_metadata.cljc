(ns com.sixsq.slipstream.ssclj.resources.spec.resource-metadata
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as cimi-common]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-action :as action]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-attribute :as attribute]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-capability :as capability]
    [com.sixsq.slipstream.ssclj.resources.spec.resource-metadata-value-scope :as value-scope]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(s/def ::typeURI ::cimi-core/uri)


(s/def ::name ::cimi-core/resource-name)


(s/def ::resource-metadata
  (su/only-keys-maps cimi-common/common-attrs
                     {:req-un [::typeURI]
                      :opt-un [::attribute/attributes
                               ::value-scope/vscope
                               ::capability/capabilities
                               ::action/actions]}))
