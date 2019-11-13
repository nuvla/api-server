(ns sixsq.nuvla.server.resources.configuration-vpn-api
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :as p]
    [sixsq.nuvla.server.resources.spec.configuration-template-vpn-api :as cts-vpn-api]))


(def ^:const service "vpn-api")

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-vpn-api/schema))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::cts-vpn-api/schema-create))
(defmethod p/create-validate-subtype service
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::cts-vpn-api/schema))
