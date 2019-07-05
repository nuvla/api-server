(ns sixsq.nuvla.server.resources.nuvlabox-peripheral-0
  "
The nuvlabox-peripheral (version 0) describes an attached peripheral.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-0 :as nb-peripheral-0]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def schema-version 0)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-peripheral-0/schema))


(defmethod nb-peripheral/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-peripheral/ns ::nb-peripheral-0/schema schema-version))


(defn initialize
  []
  (std-crud/initialize nb-peripheral/resource-type ::nb-peripheral-0/schema)
  (md/register resource-metadata))
