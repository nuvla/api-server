(ns com.sixsq.slipstream.ssclj.resources.spec.external-object-template-public
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object :as eo]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(def template-resource-keys-spec
  (u/remove-req eo/common-external-object-attrs #{::eo/state}))

;; Defines the contents of the public template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :externalObjectTemplate here.
(s/def ::externalObjectTemplate
  (su/only-keys-maps c/template-attrs
                     template-resource-keys-spec))

(s/def ::external-object-create
  (su/only-keys-maps c/create-attrs
                     {:req-un [::externalObjectTemplate]}))
