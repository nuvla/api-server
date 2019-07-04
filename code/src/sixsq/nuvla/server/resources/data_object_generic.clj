(ns sixsq.nuvla.server.resources.data-object-generic
  "
This resource represents an object in S3 that can only be accessed via
credentials (either direct infrastructure credentials or via pre-signed URLs).
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template-generic :as dot]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.data-object-generic :as do-generic]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


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

(defn initialize
  []
  (std-crud/initialize do/resource-type ::do-generic/schema)
  (md/register (gen-md/generate-metadata ::ns ::do-generic/schema)))

