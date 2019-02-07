(ns sixsq.nuvla.server.resources.configuration-slipstream
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :as p]
    [sixsq.nuvla.server.resources.configuration-template-slipstream :as tpl]
    [sixsq.nuvla.server.resources.spec.configuration-template-slipstream :as configuration-template]))


(def ^:const service "slipstream")


(def ^:const instance-url (str p/resource-url "/" service))


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
  {:resourceURI p/create-uri
   :template    {:href "configuration-template/slipstream"}})


;;
;; initialization: create initial service configuration if necessary
;;
(defn initialize
  []
  ;; FIXME: this is a nasty hack to ensure configuration template is available
  (tpl/initialize)

  (std-crud/initialize p/resource-url ::configuration-template/slipstream)
  (std-crud/add-if-absent "configuration/slipstream" p/resource-url create-template))
