(ns sixsq.nuvla.server.resources.configuration-nuvla
  "
Contains the core configuration of the Nuvla server. This resource must always
exist. If it isn't found when the server starts, then it will be created with
default values.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :as p]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-template-nuvla :as tpl-nuvla]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as ct-nuvla]))


(def ^:const service "nuvla")


(def ^:const config-instance-url (str p/resource-type "/" service))


(def ^:const tpl-instance-url (str ct/resource-type "/" tpl-nuvla/service))


;;
;; initialization: create initial service configuration if necessary
;;

(defn initialize
  []
  ;; FIXME: this is a nasty hack to ensure configuration template is available
  (tpl-nuvla/initialize)

  (std-crud/initialize p/resource-type ::ct-nuvla/schema)

  (let [create-template {:resource-type p/create-type
                         :template      {:href tpl-instance-url}}]
    (std-crud/add-if-absent config-instance-url p/resource-type create-template)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-nuvla/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ct-nuvla/schema-create))


(defmethod p/create-validate-subtype service
  [resource]
  (create-validate-fn resource))


