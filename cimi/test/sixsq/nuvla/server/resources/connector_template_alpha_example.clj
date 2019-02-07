(ns sixsq.nuvla.server.resources.connector-template-alpha-example
  "This is an example ConnectorTemplate resource that shows how a
   concrete ConnectorTemplate resource would be defined and also to
   provide a concrete resource for testing."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector-template :as p]
    [sixsq.nuvla.server.resources.spec.connector-template]
    [sixsq.nuvla.server.resources.spec.connector-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]))

(def ^:const cloud-service-type "alpha")

;;
;; schemas
;;

(s/def :cimi.connector-template.alpha/alphaKey pos-int?)
(s/def :cimi.connector-template.alpha/objectStoreEndpoint ::ps/objectStoreEndpoint)

;; Defines the contents of the alpha ConnectorTemplate resource itself.
(s/def :cimi/connector-template.alpha
  (su/only-keys-maps ps/resource-keys-spec
                     {:req-un [:cimi.connector-template.alpha/alphaKey]
                      :opt-un [:cimi.connector-template.alpha/objectStoreEndpoint]}))

;; Defines the contents of the alpha template used in a create resource.
(s/def :cimi.connector-template.alpha/template
  (su/only-keys-maps ps/template-keys-spec
                     {:opt-un [:cimi.connector-template.alpha/alphaKey
                               :cimi.connector-template.alpha/objectStoreEndpoint]}))

(s/def :cimi/connector-template.alpha-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [:cimi.connector-template.alpha/template]}))

;;
;; resource
;;
(def ^:const resource
  {:cloudServiceType cloud-service-type
   :alphaKey         1001})

;;
;; initialization: register this connector template
;;
(defn initialize
  []
  (p/register resource))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/connector-template.alpha))
(defmethod p/validate-subtype cloud-service-type
  [resource]
  (validate-fn resource))
