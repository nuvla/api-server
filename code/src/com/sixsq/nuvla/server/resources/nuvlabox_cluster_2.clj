(ns com.sixsq.nuvla.server.resources.nuvlabox-cluster-2
  "
The version 2 of nuvlabox-cluster resource
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.nuvlabox-cluster :as nb-cluster]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-cluster-2 :as nb-cluster-2]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def schema-version 2)


;;
;; multimethod for validation
;;


(def validate-fn (u/create-spec-validation-fn ::nb-cluster-2/schema))


(defmethod nb-cluster/validate-subtype schema-version
  [resource]
  (validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::nb-cluster/ns ::nb-cluster-2/schema))


(defn initialize
  []
  (std-crud/initialize nb-cluster/resource-type ::nb-cluster-2/schema)
  (md/register resource-metadata))