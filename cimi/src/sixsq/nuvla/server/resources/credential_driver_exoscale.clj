(ns sixsq.nuvla.server.resources.credential-driver-exoscale
    (:require
      [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
      [sixsq.nuvla.server.resources.common.utils :as u]
      [sixsq.nuvla.server.resources.credential :as p]
      [sixsq.nuvla.server.resources.credential-template-driver-exoscale :as tpl]
      [sixsq.nuvla.server.resources.spec.credential-driver-exoscale :as driver]))

;;
;; convert template to credential
;;

(defmethod p/tpl->credential tpl/credential-type
           [{:keys [type method exoscale-api-key exoscale-api-secret-key acl]} request]
           (let [resource (cond-> {:resource-type p/resource-type
                                   :type          type
                                   :method        method
                                   :exoscale-api-key           exoscale-api-key
                                   :exoscale-api-secret-key    exoscale-api-secret-key}
                                  acl (assoc :acl acl))]
                [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::driver/schema))
(defmethod p/validate-subtype tpl/credential-type
           [resource]
           (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::driver/schema-create))
(defmethod p/create-validate-subtype tpl/credential-type
           [resource]
           (create-validate-fn resource))


;;
;; initialization
;;

(defn initialize
      []
      (std-crud/initialize p/resource-type ::driver/schema))
