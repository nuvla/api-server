(ns sixsq.nuvla.server.resources.configuration-nuvla
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :as p]
    [sixsq.nuvla.server.resources.configuration-template-nuvla :as tpl]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as configuration-template]))


(def ^:const service "nuvla")


(def ^:const instance-url (str p/resource-type "/" service))


;;
;; initialization: create initial service configuration if necessary
;;

(defn initialize
  []
  ;; FIXME: this is a nasty hack to ensure configuration template is available
  (tpl/initialize)

  (std-crud/initialize p/resource-type ::configuration-template/slipstream)
  (std-crud/add-if-absent "configuration/nuvla" p/resource-type create-template))


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
  {:resource-type p/create-type
   :template      {:href "configuration-template/slipstream"}})
