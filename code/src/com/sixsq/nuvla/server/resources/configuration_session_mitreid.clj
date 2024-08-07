(ns com.sixsq.nuvla.server.resources.configuration-session-mitreid
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration :as p]
    [com.sixsq.nuvla.server.resources.spec.configuration-template-session-mitreid :as cts-mitreid]))


(def ^:const service "session-mitreid")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-mitreid/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::cts-mitreid/schema-create))


(defmethod p/create-validate-subtype service
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::cts-mitreid/schema))
