(ns sixsq.nuvla.server.resources.data-object-template-generic
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.spec.data-object-generic :as do-generic]
    [sixsq.nuvla.server.resources.spec.data-object-template-generic :as dot-generic]))


(def ^:const data-object-type "generic")


;;
;; resource
;;

(def ^:const resource
  {:type         data-object-type
   :content-type "application/octet-stream"})


;;
;; initialization: register this external object generic template
;;

(defn initialize
  []
  (dot/register resource))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::do-generic/schema))


(defmethod do/validate-subtype data-object-type
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::dot-generic/schema-create))


(defmethod do/create-validate-subtype data-object-type
  [resource]
  (create-validate-fn resource))


(def validate-fn (u/create-spec-validation-fn ::dot-generic/template))


(defmethod dot/validate-subtype-template data-object-type
  [resource]
  (validate-fn resource))
