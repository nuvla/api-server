(ns sixsq.nuvla.server.resources.credential-totp-2fa
  "
A secret generated by Nuvla server that is required to authenticate user with
2FA TOTP.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-totp-2fa :as tpl-totp-2fa]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-totp-2fa :as
     ct-2fa-totp-spec]
    [sixsq.nuvla.server.resources.spec.credential-totp-2fa :as spec-totp-2fa]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::spec-totp-2fa/schema))


(defn initialize
  []
  (md/register resource-metadata))


;;
;; convert template to credential
;;

(defmethod p/tpl->credential tpl-totp-2fa/credential-subtype
  [{:keys [subtype method secret parent acl]} _request]
  [nil (cond-> {:resource-type p/resource-type
                :subtype       subtype
                :method        method
                :secret        secret}
               acl (assoc :acl acl)
               parent (assoc :parent parent))])


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::spec-totp-2fa/schema))


(defmethod p/validate-subtype tpl-totp-2fa/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ct-2fa-totp-spec/schema-create))


(defmethod p/create-validate-subtype tpl-totp-2fa/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; operations
;;

(defn set-collection-ops
  [{:keys [id] :as resource} request]
  (if (a/can-add? resource request)
    (assoc resource :operations [(u/operation-map id :add)])
    (dissoc resource :operations)))


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [ops (cond-> []
                    (a/can-edit? resource request) (conj (u/operation-map id :edit))
                    (a/can-delete? resource request) (conj (u/operation-map id :delete)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod p/set-credential-operations tpl-totp-2fa/credential-subtype
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (set-collection-ops resource request)
    (set-resource-ops resource request)))
