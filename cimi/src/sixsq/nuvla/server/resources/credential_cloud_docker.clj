(ns sixsq.nuvla.server.resources.credential-cloud-docker
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-cloud-docker :as tpl]
    [sixsq.nuvla.server.resources.spec.credential-cloud-docker :as docker]))

;;
;; convert template to credential
;;

(defmethod p/tpl->credential tpl/credential-type
  [{:keys [type method connector key secret quota acl]} request]
  (let [resource (cond-> {:resource-type p/resource-type
                          :type          type
                          :method        method
                          :connector     connector
                          :quota         (or quota (:quota tpl/resource))
                          :key           key
                          :secret        secret}
                         acl (assoc :acl acl))]
    [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::docker/schema))
(defmethod p/validate-subtype tpl/credential-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::docker/schema-create))
(defmethod p/create-validate-subtype tpl/credential-type
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize p/resource-type ::docker/schema))
