(ns sixsq.nuvla.server.resources.credential-template-cloud-alpha
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector-alpha-example :as ct]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-cloud :as ctc]
    [sixsq.nuvla.server.util.spec :as su]))

;; Defines the contents of the cloud-alpha CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     ctc/credential-template-cloud-keys-spec))

;; Template.
(def ^:const credential-type (str "cloud-cred-" ct/cloud-service-type))
(def ^:const method (str "store-cloud-cred-" ct/cloud-service-type))

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
   :name        "Alpha cloud credentials store"
   :description "Stores user cloud credentials for Alpha"
   :quota       20
   :key         ""
   :secret      ""
   :acl         resource-acl})

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this Credential template
;;
(defn initialize
  []
  (p/register resource))
