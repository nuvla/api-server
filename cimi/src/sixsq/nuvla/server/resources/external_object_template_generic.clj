(ns sixsq.nuvla.server.resources.external-object-template-generic
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.external-object :as eo]
    [sixsq.nuvla.server.resources.external-object-template :as eot]
    [sixsq.nuvla.server.resources.spec.external-object-generic :as eo-generic]
    [sixsq.nuvla.server.resources.spec.external-object-template-generic :as eot-generic]))

(def ^:const objectType "generic")


;;
;; resource
;;
(def ^:const resource
  {:objectType      objectType
   :contentType     "content/type"
   :objectStoreCred {:href "credential/cloud-cred"}
   :bucketName      "bucket-name"
   :objectName      "object/name"})


;;
;; initialization: register this external object generic template
;;
(defn initialize
  []
  (eot/register resource))


;;
;; multimethods for validation
;;
(def validate-fn (u/create-spec-validation-fn ::eo-generic/external-object))
(defmethod eo/validate-subtype objectType
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::eot-generic/external-object-create))
(defmethod eo/create-validate-subtype objectType
  [resource]
  (create-validate-fn resource))

(def validate-fn (u/create-spec-validation-fn ::eot-generic/externalObjectTemplate))
(defmethod eot/validate-subtype-template objectType
  [resource]
  (validate-fn resource))
