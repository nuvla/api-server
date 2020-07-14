(ns sixsq.nuvla.server.resources.data-object-public
  "
This resource represents an object in S3 that can be accessed by anyone via a
fixed URL.
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
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.data-object-public :as do-public]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::do-public/schema))


(defmethod do/validate-subtype dot/data-object-subtype
  [resource]
  (validate-fn resource))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::do-public/schema))


(defn initialize
  []
  (std-crud/initialize do/resource-type ::do-public/schema)
  (md/register resource-metadata))


(defmethod do/ready-subtype dot/data-object-subtype
  [resource request]
  (-> resource
      (a/throw-cannot-edit request)
      (do/verify-state #{do/state-uploading} "ready")
      (s3/set-public-read-object)
      (assoc :state do/state-ready)
      (s3/add-s3-url)
      (s3/add-s3-bytes)
      (s3/add-s3-md5sum)
      (u/update-timestamps)
      (u/set-updated-by request)
      (db/edit request)))


(defmethod do/download-subtype dot/data-object-subtype
  [{:keys [url] :as resource} request]
  (do/verify-state resource #{do/state-ready} "download")
  (log/info "Public download url: " url)
  url)
