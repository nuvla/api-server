(ns com.sixsq.slipstream.ssclj.resources.spec.external-object-template-report
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.core :as cimi-core]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-report :as eor]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))


(def template-resource-keys-spec
  (u/remove-req eor/external-object-report-keys-spec #{::eo/state
                                                       ::eo/objectName
                                                       ::eo/bucketName
                                                       ::eo/objectStoreCred}))

(s/def ::filename ::cimi-core/nonblank-string)

;; Defines the contents of the generic template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :externalObjectTemplate here.
(s/def ::externalObjectTemplate
  (su/only-keys-maps c/template-attrs
                     template-resource-keys-spec
                     {:req-un [::filename]}))

(s/def ::external-object-create
  (su/only-keys-maps c/create-attrs
                     {:req-un [::externalObjectTemplate]}))
