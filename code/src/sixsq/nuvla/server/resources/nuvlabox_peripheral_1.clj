(ns sixsq.nuvla.server.resources.nuvlabox-peripheral-1
  "
The nuvlabox-peripheral (version 1) describes an attached peripheral.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-1 :as nb-peripheral-1]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def schema-version 1)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-peripheral-1/schema))


(defmethod nb-peripheral/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-peripheral/ns ::nb-peripheral-1/schema schema-version))


(defn initialize
  []
  (std-crud/initialize nb-peripheral/resource-type ::nb-peripheral-1/schema)
  (md/register resource-metadata))
