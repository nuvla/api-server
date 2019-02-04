(ns com.sixsq.slipstream.ssclj.resources.configuration-slipstream
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.std-crud :as std-crud]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.configuration :as p]
    [com.sixsq.slipstream.ssclj.resources.configuration-template-slipstream :as tpl]
    [com.sixsq.slipstream.ssclj.resources.spec.configuration-template-slipstream :as configuration-template]))


(def ^:const service "slipstream")


(def ^:const instance-url (str p/resource-url "/" service))


(def ConfigurationDescription
  tpl/desc)

;;
;; description
;;

(def ^:const desc ConfigurationDescription)

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::configuration-template/slipstream))
(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::configuration-template/slipstream-create))
(defmethod p/create-validate-subtype service
  [resource]
  (create-validate-fn resource))


(def create-template
  {:resourceURI           p/create-uri
   :configurationTemplate {:href "configuration-template/slipstream"}})


;;
;; initialization: create initial service configuration if necessary
;;
(defn initialize
  []
  ;; FIXME: this is a nasty hack to ensure configuration template is available
  (tpl/initialize)

  (std-crud/initialize p/resource-url ::configuration-template/slipstream)
  (std-crud/add-if-absent "configuration/slipstream" p/resource-url create-template))
