(ns sixsq.nuvla.server.resources.nuvlabox-1
  "
The nuvlabox (version 1) contains attributes to describe and configure
a NuvlaBox.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.nuvlabox-1 :as nb-1]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def schema-version 1)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-1/schema))


(defmethod nb/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; multimethod for commission
;;

(defmethod nb/commission schema-version
  [resource request]
  (nb-utils/commission resource request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb/ns ::nb-1/schema))


(defn initialize
  []
  (std-crud/initialize nb/resource-type ::nb-1/schema)
  (md/register resource-metadata))
