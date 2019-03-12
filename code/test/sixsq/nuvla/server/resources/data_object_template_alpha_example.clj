(ns sixsq.nuvla.server.resources.data-object-template-alpha-example
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as do-resource]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.data-object :as do]
    [sixsq.nuvla.server.util.spec :as su]))

(def ^:const type "alpha")

;;
;; schemas
;;

(s/def :cimi.data-object.alpha/alphaKey pos-int?)

(def data-object-keys-spec
  (u/remove-req do/common-data-object-attrs #{::do/bucket
                                              ::do/object
                                              ::do/credential}))

(def data-object-alpha-keys-spec
  (su/merge-keys-specs [data-object-keys-spec
                        {:req-un [:cimi.data-object.alpha/alphaKey]}]))

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        data-object-alpha-keys-spec]))

(s/def :cimi/data-object.alpha
  (su/only-keys-maps resource-keys-spec))


(s/def :cimi.data-object-template.alpha/template
  (su/only-keys-maps c/template-attrs
                     (u/remove-req data-object-alpha-keys-spec #{::do/state})))

(s/def :cimi/data-object-template.alpha-create
  (su/only-keys-maps c/create-attrs
                     {:req-un [:cimi.data-object-template.alpha/template]}))

;;
;; template resource
;;

(def ^:const resource-template
  {:type     type
   :alphaKey 1001})


;;
;; initialization: register this data object template
;;

(defn initialize
  []
  (dot/register resource-template))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/data-object.alpha))
(defmethod do-resource/validate-subtype type
  [resource]
  (validate-fn resource))

(def validate-fn (u/create-spec-validation-fn :cimi/data-object-template.alpha-create))
(defmethod do-resource/create-validate-subtype type
  [resource]
  (validate-fn resource))

(def validate-fn (u/create-spec-validation-fn :cimi.data-object-template.alpha/template))
(defmethod dot/validate-subtype-template type
  [resource]
  (validate-fn resource))
