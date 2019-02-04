(ns com.sixsq.slipstream.ssclj.resources.external-object-template-report
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object-template :as eot]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-template-report :as eot-report]))

(def ^:const objectType "report")

(def ExternalObjectTemplateReportDescription
  (merge eot/ExternalObjectTemplateDescription
         {:runUUID   {:displayName "Deployment UUID"
                      :category    "general"
                      :description "Deployment UUID"
                      :type        "string"
                      :mandatory   true
                      :readOnly    false
                      :order       20}
          :component {:displayName "Component name"
                      :category    "general"
                      :description "Component which created this report"
                      :type        "string"
                      :mandatory   true
                      :readOnly    false
                      :order       21}}))


;;
;; resource
;;

(def ^:const resource
  {:objectType  objectType
   :runUUID     "uuid"
   :component   "component"
   :filename    "filename"
   :contentType "content/type"})

;;
;; description
;;
(def ^:const desc ExternalObjectTemplateReportDescription)


;;
;; initialization: register this external object report template
;;
(defn initialize
  []
  (eot/register resource desc))


;;
;; multimethods for validation
;;
(def validate-fn (u/create-spec-validation-fn ::eot-report/external-object))
(defmethod eo/validate-subtype objectType
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::eot-report/external-object-create))
(defmethod eo/create-validate-subtype objectType
  [resource]
  (create-validate-fn resource))

(def validate-fn (u/create-spec-validation-fn ::eot-report/externalObjectTemplate))
(defmethod eot/validate-subtype-template objectType
  [resource]
  (validate-fn resource))
