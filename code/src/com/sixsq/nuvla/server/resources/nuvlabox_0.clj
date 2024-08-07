(ns com.sixsq.nuvla.server.resources.nuvlabox-0
  "
The nuvlabox (version 0) contains attributes to describe and configure
a NuvlaBox.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.nuvlabox.workflow-utils :as wf-utils]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-0 :as nb-0]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def schema-version 0)


;;
;; multimethod for validation
;;

(def validate-fn (u/create-spec-validation-fn ::nb-0/schema))


(defmethod nb/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; multimethod for commission
;;

(defmethod nb/commission schema-version
  [resource request]
  (wf-utils/commission resource request))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb/ns ::nb-0/schema))


(defn initialize
  []
  (std-crud/initialize nb/resource-type ::nb-0/schema)
  (md/register resource-metadata))
