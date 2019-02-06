(ns sixsq.nuvla.server.resources.credential-template-cloud-alpha
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector-alpha-example :as ct]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-cloud :as ctc]
    [sixsq.nuvla.server.util.spec :as su]))

;; Schemas.
(s/def :cimi.credential-template.cloud-alpha/domain-name string?)

;(def credential-template-keys-spec
;  {:opt-un [:cimi.credential-template.cloud-alpha/domain-name]})

;(def credential-template-create-keys-spec credential-template-keys-spec)

;; Defines the contents of the cloud-alpha CredentialTemplate resource itself.
(s/def :cimi/credential-template.cloud-alpha
  (su/only-keys-maps ps/resource-keys-spec
                     ctc/credential-template-cloud-keys-spec))
                     ; credential-template-keys-spec))

;; Defines the contents of the cloud-alpha template used in a create resource.
;; NOTE: The name must match the key defined by the resource, :credentialTemplate here.
(s/def :cimi.credential-template.cloud-alpha/credentialTemplate
  (su/only-keys-maps ps/template-keys-spec
                     ctc/credential-template-create-keys-spec))
                     ;credential-template-create-keys-spec))

(s/def :cimi/credential-template.cloud-alpha-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [:cimi.credential-template.cloud-alpha/credentialTemplate]}))

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
   :connector   {:href "connector/abcdef"}
   :key         ""
   :secret      ""
   :acl         resource-acl})

;;
;; initialization: register this Credential template
;;
(defn initialize
  []
  (p/register resource))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/credential-template.cloud-alpha))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))
