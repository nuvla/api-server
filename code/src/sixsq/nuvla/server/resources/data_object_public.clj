(ns sixsq.nuvla.server.resources.data-object-public
  "
Resource represents an object in S3 that can be accessed by anyone.
"
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template-public :as dot]
    [sixsq.nuvla.server.resources.data.utils :as s3]
    [sixsq.nuvla.server.resources.spec.data-object-public :as do-public]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.resources.resource-metadata :as md]))


(def ^:const resource-type (u/ns->type *ns*))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::do-public/schema))


(defmethod do/validate-subtype dot/data-object-type
  [resource]
  (validate-fn resource))


;;
;; initialization
;;

(defn initialize
  []
  (std-crud/initialize do/resource-type ::do-public/schema)
  (md/register (gen-md/generate-metadata ::ns ::do-public/schema)))


(defmethod do/ready-subtype dot/data-object-type
  [resource request]
  (-> resource
      (a/can-edit-acl? request)
      (do/verify-state #{do/state-uploading} "ready")
      (s3/set-public-read-object)
      (assoc :state do/state-ready)
      (s3/add-s3-url)
      (s3/add-s3-bytes)
      (s3/add-s3-md5sum)
      (db/edit request)))


(defmethod do/download-subtype dot/data-object-type
  [{:keys [url] :as resource} request]
  (do/verify-state resource #{do/state-ready} "download")
  (log/info "Public download url: " url)
  url)
