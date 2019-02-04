(ns com.sixsq.slipstream.ssclj.resources.external-object-template-public
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-public :as eo-public]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-template-public :as eot-public]))

(def ^:const objectType "public")

(def ExternalObjectTemplatePublicDescription
  (merge eot/ExternalObjectTemplateDescription
         {:bucketName      {:displayName "Object Store bucket name"
                            :category    "general"
                            :description "Name of the bucket to store the object to"
                            :type        "string"
                            :mandatory   true
                            :readOnly    false
                            :order       21}
          :objectName      {:displayName "Object name"
                            :category    "general"
                            :description "Name of the object to store (/can/be/hierarchical)."
                            :type        "string"
                            :mandatory   true
                            :readOnly    false
                            :order       22}
          :objectStoreCred {:displayName "Object Store credentials."
                            :category    "general"
                            :description "Link to Object Store credential."
                            :type        "href"
                            :mandatory   true
                            :readOnly    false
                            :order       23}}))

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
;; description
;;
(def ^:const desc ExternalObjectTemplatePublicDescription)


;;
;; initialization: register this external object generic template
;;
(defn initialize
  []
  (eot/register resource desc))


;;
;; multimethods for validation
;;
(def validate-fn (u/create-spec-validation-fn ::eo-public/external-object))
(defmethod eo/validate-subtype objectType
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::eot-public/external-object-create))
(defmethod eo/create-validate-subtype objectType
  [resource]
  (create-validate-fn resource))

(def validate-fn (u/create-spec-validation-fn ::eot-public/externalObjectTemplate))
(defmethod eot/validate-subtype-template objectType
  [resource]
  (validate-fn resource))
