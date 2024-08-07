(ns com.sixsq.nuvla.server.resources.configuration-session-mitreid-token
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration :as p]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid-token :as cts-mitreid-token]))


(def ^:const service "session-mitreid-token")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-mitreid-token/schema))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::cts-mitreid-token/schema-create))
(defmethod p/create-validate-subtype service
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::cts-mitreid-token/schema))
