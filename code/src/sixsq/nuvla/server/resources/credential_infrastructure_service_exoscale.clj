(ns sixsq.nuvla.server.resources.credential-infrastructure-service-exoscale
  "
Provides `docker-machine` credentials for Exoscale. The attribute names
correspond exactly to those required by `docker-machine`.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-exoscale :as tpl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-exoscale :as service]
    [sixsq.nuvla.server.util.metadata :as gen-md]))

;;
;; convert template to credential
;;

(defmethod p/tpl->credential tpl/credential-subtype
  [{:keys [subtype method exoscale-api-key exoscale-api-secret-key acl]} _request]
  (let [resource (cond-> {:resource-type           p/resource-type
                          :subtype                 subtype
                          :method                  method
                          :exoscale-api-key        exoscale-api-key
                          :exoscale-api-secret-key exoscale-api-secret-key}
                         acl (assoc :acl acl))]
    [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::service/schema))


(defmethod p/validate-subtype tpl/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::service/schema-create))


(defmethod p/create-validate-subtype tpl/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::service/schema))


(defn initialize
  []
  (md/register resource-metadata))
