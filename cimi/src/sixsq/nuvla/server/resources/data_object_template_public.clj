(ns sixsq.nuvla.server.resources.data-object-template-public
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.spec.data-object-public :as do-public]
    [sixsq.nuvla.server.resources.spec.data-object-template-public :as dot-public]))

(def ^:const object-type "public")

;;
;; resource
;;
(def ^:const resource
  {:object-type       object-type
   :content-type      "content/type"
   :object-store-cred {:href "credential/cloud-cred"}
   :bucket-name       "bucket-name"
   :object-name       "object/name"})


;;
;; initialization: register this data object generic template
;;
(defn initialize
  []
  (dot/register resource))


;;
;; multimethods for validation
;;
(def validate-fn (u/create-spec-validation-fn ::do-public/data-object))
(defmethod do/validate-subtype object-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn ::dot-public/data-object-create))
(defmethod do/create-validate-subtype object-type
  [resource]
  (create-validate-fn resource))

(def validate-fn (u/create-spec-validation-fn ::dot-public/template))
(defmethod dot/validate-subtype-template object-type
  [resource]
  (validate-fn resource))
