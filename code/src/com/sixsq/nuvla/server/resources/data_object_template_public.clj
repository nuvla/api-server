(ns com.sixsq.nuvla.server.resources.data-object-template-public
  "
This template creates a resource representing an object in S3 that can be
accessed by anyone via a fixed URL.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.data-object :as do]
    [com.sixsq.nuvla.server.resources.data-object-template :as dot]
    [com.sixsq.nuvla.server.resources.spec.data-object-template-public :as dot-public]))


(def ^:const data-object-subtype "public")


;;
;; resource
;;

(def ^:const resource
  {:subtype      data-object-subtype
   :content-type "application/octet-stream"})


;;
;; initialization: register this data object generic template
;;

(defn initialize
  []
  (dot/register resource))


;;
;; multimethods for validation
;;

(def create-validate-fn (u/create-spec-validation-fn ::dot-public/schema-create))


(defmethod do/create-validate-subtype data-object-subtype
  [resource]
  (create-validate-fn resource))


(def validate-fn (u/create-spec-validation-fn ::dot-public/template))


(defmethod dot/validate-subtype-template data-object-subtype
  [resource]
  (validate-fn resource))
