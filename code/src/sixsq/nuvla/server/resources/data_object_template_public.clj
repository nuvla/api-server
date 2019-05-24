(ns sixsq.nuvla.server.resources.data-object-template-public
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-object :as do]
    [sixsq.nuvla.server.resources.data-object-template :as dot]
    [sixsq.nuvla.server.resources.spec.data-object-template-public :as dot-public]))


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
