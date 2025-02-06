(ns com.sixsq.nuvla.server.resources.nuvlabox-status-0
  "
The nuvlabox-status (version 0) contains attributes to describe available
resources and peripherals.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-status-0 :as nb-status-0]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def schema-version 0)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-status-0/schema))


(defmethod nb-status/validate-subtype schema-version
  [resource]
  (validate-fn resource))

(defmethod nb-status/spec-subtype schema-version
  [_resource]
  ::nb-status-0/schema)


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-status/ns ::nb-status-0/schema))


(defn initialize
  []
  (std-crud/initialize nb-status/resource-type ::nb-status-0/schema)
  (md/register resource-metadata))
