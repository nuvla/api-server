(ns sixsq.nuvla.server.resources.external-object-public
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.external-object :as eo]
    [sixsq.nuvla.server.resources.external-object-template-public :as eot]
    [sixsq.nuvla.server.resources.external-object.utils :as s3]
    [sixsq.nuvla.server.resources.spec.external-object-public :as eo-public]))


;; multimethods for validation

(def validate-fn (u/create-spec-validation-fn ::eo-public/external-object))
(defmethod eo/validate-subtype eot/objectType
  [resource]
  (validate-fn resource))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize eo/resource-url ::eo-public/external-object))

(defmethod eo/ready-subtype eot/objectType
  [resource request]
  (-> resource
      (a/can-modify? request)
      (eo/verify-state #{eo/state-uploading} "ready")
      (s3/set-public-read-object)
      (assoc :state eo/state-ready)
      (s3/add-s3-url)
      (s3/add-s3-size)
      (s3/add-s3-md5sum)
      (db/edit request)))

(defmethod eo/download-subtype eot/objectType
  [{:keys [URL] :as resource} request]
  (eo/verify-state resource #{eo/state-ready} "download")
  (log/info "Public download url: " URL)
  URL)