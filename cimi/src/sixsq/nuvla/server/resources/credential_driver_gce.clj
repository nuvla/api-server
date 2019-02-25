(ns sixsq.nuvla.server.resources.credential-driver-gce
    "
Sets the docker-machine compliant attribute names and values
for the Exoscale driver
"
    (:require
      [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
      [sixsq.nuvla.server.resources.common.utils :as u]
      [sixsq.nuvla.server.resources.credential :as p]
      [sixsq.nuvla.server.resources.credential-template-driver-gce :as tpl]
      [sixsq.nuvla.server.resources.spec.credential-driver-gce :as driver]))

;;
;; convert template to credential
;;

(defmethod p/tpl->credential tpl/credential-type
           [{:keys [type method project-id private-key-id private-key client-email client-id acl]} request]
           (let [resource (cond-> {:resource-type p/resource-type
                                   :type          type
                                   :method        method
                                   :project-id    project-id
                                   :private-key-id  private-key-id
                                   :private-key   private-key
                                   :client-email  client-email
                                   :client-id     client-id}
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
