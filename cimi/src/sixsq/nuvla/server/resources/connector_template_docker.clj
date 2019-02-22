(ns sixsq.nuvla.server.resources.connector-template-docker
  "Connector template for Docker"
  (:require
    [clojure.set :as set]
    [sixsq.nuvla.server.resources.spec.connector-template-docker :as ctd]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const cloud-service-type "docker")


(def ^:const resource-name "Docker")


(def ^:const resource-url cloud-service-type)


;;
;; resource
;;

;; defaults for the template
(def ^:const resource
  (merge (select-keys p/connector-reference-attrs-defaults ctd/keys-spec)
         {:cloudServiceType cloud-service-type
          :endpoint         "https://<HOSTNAME>:2376"
          :updateClientURL  "https://<IP>/downloads/dockerclient.tgz"}))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ctd/schema))
(defmethod p/validate-subtype cloud-service-type
  [resource]
  (validate-fn resource))


;;
;; initialization: register this connector template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ctd/schema)))
