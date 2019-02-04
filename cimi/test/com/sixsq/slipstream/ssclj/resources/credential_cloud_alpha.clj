(ns com.sixsq.slipstream.ssclj.resources.credential-cloud-alpha
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.credential :as p]
    [com.sixsq.slipstream.ssclj.resources.credential-template-cloud-alpha :as tpl]
    [com.sixsq.slipstream.ssclj.resources.spec.credential :as cred]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template :as ps]
    [com.sixsq.slipstream.ssclj.resources.spec.credential-template-cloud :as ctc]
    [com.sixsq.slipstream.ssclj.util.spec :as su]))

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
