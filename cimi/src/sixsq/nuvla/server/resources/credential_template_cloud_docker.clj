(ns sixsq.nuvla.server.resources.credential-template-cloud-docker
    "This CredentialTemplate allows creating a Cloud Credential instance to hold
    cloud credentials for Docker cloud."
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector-template-docker :as ct]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.credential-template-cloud :as ctc]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-cloud-docker :as docker-tpl]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type (ctc/cred-type ct/cloud-service-type))


(def ^:const resource-name "Docker")


(def ^:const resource-url credential-type)


(def ^:const method (ctc/cred-method ct/cloud-service-type))


;;
;; resource
;;

(def ^:const resource (ctc/gen-resource {} ct/cloud-service-type))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::docker-tpl/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::docker-tpl/schema)))
