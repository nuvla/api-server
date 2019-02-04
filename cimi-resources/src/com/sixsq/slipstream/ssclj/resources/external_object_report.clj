(ns com.sixsq.slipstream.ssclj.resources.external-object-report
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.crud :as crud]
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration :as p]
    [com.sixsq.slipstream.ssclj.resources.configuration-slipstream :as conf-ss]
    [com.sixsq.slipstream.ssclj.resources.external-object :as eo]
    [com.sixsq.slipstream.ssclj.resources.external-object-template-report :as eot]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-report :as eo-report]
    [com.sixsq.slipstream.ssclj.resources.spec.external-object-template-report]))


(def ExternalObjectReportDescription
  eot/ExternalObjectTemplateReportDescription)

;;
;; description
;;
(def ^:const desc ExternalObjectReportDescription)


(defn ss-conf
  "Returns SlipStream configuration."
  []
  (crud/retrieve-by-id-as-admin (str p/resource-url "/" conf-ss/service)))


(defn object-name
  [{:keys [runUUID filename]}]
  (format "%s/%s" runUUID filename))


(defmethod eo/tpl->externalObject eot/objectType
  [resource]
  (let [{:keys [reportsObjectStoreBucketName reportsObjectStoreCreds]} (ss-conf)]
    (-> resource
        (merge {:objectStoreCred {:href reportsObjectStoreCreds}
                :bucketName      reportsObjectStoreBucketName
                :objectName      (object-name resource)})
        (dissoc :filename))))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::eo-report/external-object))
(defmethod eo/validate-subtype eot/objectType
  [resource]
  (validate-fn resource))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize eo/resource-url :eo-report/external-object))
