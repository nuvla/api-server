(ns com.sixsq.slipstream.ssclj.resources.external-object-template-alpha-example
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo-resource]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.spec.common :as c]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object :as eo]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

(def ^:const objectType "alpha")

;;
;; schemas
;;

(s/def :cimi.external-object.alpha/alphaKey pos-int?)

(def external-object-keys-spec
  (u/remove-req eo/common-external-object-attrs #{::eo/bucketName
                                               ::eo/objectName
                                               ::eo/objectStoreCred}))

(def external-object-alpha-keys-spec
  (su/merge-keys-specs [external-object-keys-spec
                        {:req-un [:cimi.external-object.alpha/alphaKey]}]))

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        external-object-alpha-keys-spec]))

(s/def :cimi/external-object.alpha
  (su/only-keys-maps resource-keys-spec))


(s/def :cimi.external-object-template.alpha/externalObjectTemplate
  (su/only-keys-maps c/template-attrs
                     (u/remove-req external-object-alpha-keys-spec #{::eo/state})))

(s/def :cimi/external-object-template.alpha-create
  (su/only-keys-maps c/create-attrs
                     {:req-un [:cimi.external-object-template.alpha/externalObjectTemplate]}))

;;
;; template resource
;;

(def ^:const resource-template
  {:objectType objectType
   :alphaKey   1001})


;;
;; initialization: register this external object template
;;

(defn initialize
  []
  (eot/register resource-template))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/external-object.alpha))
(defmethod eo-resource/validate-subtype objectType
  [resource]
  (validate-fn resource))

(def validate-fn (u/create-spec-validation-fn :cimi/external-object-template.alpha-create))
(defmethod eo-resource/create-validate-subtype objectType
  [resource]
  (validate-fn resource))

(def validate-fn (u/create-spec-validation-fn :cimi.external-object-template.alpha/externalObjectTemplate))
(defmethod eot/validate-subtype-template objectType
  [resource]
  (validate-fn resource))
