(ns com.sixsq.slipstream.ssclj.resources.credential-template-api-key
  "
Allows an API key-secret pair to be created that allows the holder of the
secret to access the server. The credential can optionally be limited in time.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.credential-template :as p]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template-api-key :as ct-api-key]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


(def ^:const credential-type "api-key")


(def ^:const resource-name "API Key")


(def ^:const resource-url credential-type)


(def ^:const method "generate-api-key")


(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "USER"
                            :type      "ROLE"
                            :right     "VIEW"}]})

;;
;; resource
;;

(def ^:const resource
  {:type        credential-type
   :method      method
   :name        "Generate API Key"
   :description "generates an API key and stores hash"
   :ttl         0
   :acl         resource-acl
   :resourceMetadata "resource-metadata/credential-template-api-key"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-api-key/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;

(defn initialize
  []
  (p/register resource)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ct-api-key/schema)))


