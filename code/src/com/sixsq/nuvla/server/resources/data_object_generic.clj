(ns com.sixsq.nuvla.server.resources.data-object-generic
  "
This resource represents an object in S3 that can only be accessed via
credentials (either direct infrastructure credentials or via pre-signed URLs).
"
  (:require
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.data-object :as do]
    [com.sixsq.nuvla.server.resources.data-object-template-generic :as dot]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.data-object-generic :as do-generic]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::do-generic/schema))


(defmethod do/validate-subtype dot/data-object-type
  [resource]
  (validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::do-generic/schema))


(defn initialize
  []
  (std-crud/initialize do/resource-type ::do-generic/schema)
  (md/register resource-metadata))

