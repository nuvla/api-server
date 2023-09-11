(ns sixsq.nuvla.server.resources.configuration-template-nuvla
  "
This configuration-template contains the core configuration attributes of the
Nuvla platform. In particular, the configuration for an SMTP (mail relay)
server should be provided. If this isn't provided email validations and other
notifications will not work.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as ct-nuvla]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const service "nuvla")


;;
;; resource
;;

(def ^:const resource
  {:service     service
   :name        "Nuvla"
   :description "Nuvla Service Configuration"

   :smtp-port   465
   :smtp-ssl    true
   :smtp-debug  true
   })


;;
;; initialization: register this configuration-template
;;


(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::ct-nuvla/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::ct-nuvla/schema-create "create"))


(def initialization-order 10)

(defn initialize
  []
  (p/register resource)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-nuvla/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))
