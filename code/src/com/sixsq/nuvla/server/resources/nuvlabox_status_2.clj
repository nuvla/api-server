(ns com.sixsq.nuvla.server.resources.nuvlabox-status-2
  "
The nuvlabox-status (version 2) contains attributes to describe available
resources.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-status-2 :as nb-status-2]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def schema-version 2)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-status-2/schema))


(defmethod nb-status/validate-subtype schema-version
  [resource]
  (validate-fn resource))

;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-status/ns ::nb-status-2/schema))


(defn initialize
  []
  (std-crud/initialize nb-status/resource-type ::nb-status-2/schema)
  (md/register resource-metadata))
