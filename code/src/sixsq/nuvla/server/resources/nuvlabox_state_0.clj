(ns sixsq.nuvla.server.resources.nuvlabox-state-0
  "
The nuvlabox-state (version 0) contains attributes to describe available
resources and peripherals.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.nuvlabox-state :as nb-state]
    [sixsq.nuvla.server.resources.spec.nuvlabox-state-0 :as nb-state-0]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [clojure.tools.logging :as log]))


(def schema-version 0)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-state-0/schema))


(defmethod nb-state/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize nb-state/resource-type ::nb-state-0/schema)
  (md/register (gen-md/generate-metadata ::ns ::nb-state/ns ::nb-state-0/schema schema-version)))
