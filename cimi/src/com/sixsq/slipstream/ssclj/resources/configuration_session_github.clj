(ns com.sixsq.slipstream.ssclj.resources.configuration-session-github
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration :as p]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template-session-github :as cts-github]))

(def ^:const service "session-github")


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cts-github/schema))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::cts-github/schema-create))
(defmethod p/create-validate-subtype service
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize p/resource-url ::cts-github/schema))
