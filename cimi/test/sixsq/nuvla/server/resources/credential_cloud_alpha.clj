(ns sixsq.nuvla.server.resources.credential-cloud-alpha
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-cloud-alpha :as tpl]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-cloud :as ctc]
    [sixsq.nuvla.server.util.spec :as su]))

(s/def :cimi/credential.cloud-alpha
  (su/only-keys-maps cred/credential-keys-spec
                     ctc/credential-template-cloud-keys-spec))

(s/def :cimi/credential.cloud-alpha.create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [:cimi.credential-template.cloud-alpha/credentialTemplate]}))

;;
;; convert template to credential
;;
(defmethod p/tpl->credential tpl/credential-type
  [{:keys [type method quota connector key secret acl]} request]
  (let [resource (cond-> {:resourceURI p/resource-uri
                          :type        type
                          :method      method
                          :quota       quota
                          :connector   connector
                          :key         key
                          :secret      secret}
                         acl (assoc :acl acl))]
    [nil resource]))

;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn :cimi/credential.cloud-alpha))
(defmethod p/validate-subtype tpl/credential-type
  [resource]
  (validate-fn resource))

(def create-validate-fn (u/create-spec-validation-fn :cimi/credential.cloud-alpha.create))
(defmethod p/create-validate-subtype tpl/credential-type
  [resource]
  (create-validate-fn resource))
