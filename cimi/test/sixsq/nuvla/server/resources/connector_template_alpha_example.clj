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

(s/def ::alphaKey pos-int?)
(s/def ::objectStoreEndpoint ::ps/objectStoreEndpoint)

;; Defines the contents of the alpha ConnectorTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     {:req-un [::alphaKey]
                      :opt-un [::objectStoreEndpoint]}))

;; Defines the contents of the alpha template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     {:opt-un [::alphaKey
                               ::objectStoreEndpoint]}))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [::template]}))

;;
;; resource
;;

(def ^:const resource
  {:cloudServiceType cloud-service-type
   :alphaKey         1001})

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::schema))
(defmethod p/validate-subtype cloud-service-type
  [resource]
  (validate-fn resource))


;;
;; initialization: register this connector template
;;
(defn initialize
  []
  (p/register resource))
