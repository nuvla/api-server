(ns sixsq.nuvla.server.resources.credential-infrastructure-service-helm-repo
  "
Provides the credentials necessary to access a Helm repo service.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as p]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-helm-repo
     :as cred-tpl-helm-repo]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-infrastructure-service-helm-repo
     :as cred-helm-repo]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-helm-repo
     :as cred-tpl-helm-repo-spec]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; convert template to credential
;;

(defmethod p/tpl->credential cred-tpl-helm-repo/credential-subtype
  [{:keys [subtype method username password parent acl]} _request]
  (let [resource (cond-> {:resource-type p/resource-type
                          :subtype       subtype
                          :method        method
                          :username      username
                          :password      password
                          :parent        parent}
                         acl (assoc :acl acl))]
    [nil resource]))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::cred-helm-repo/schema))


(defmethod p/validate-subtype cred-tpl-helm-repo/credential-subtype
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::cred-tpl-helm-repo-spec/schema-create))


(defmethod p/create-validate-subtype cred-tpl-helm-repo/credential-subtype
  [resource]
  (create-validate-fn resource))


;;
;; operations
;;


(defn set-resource-ops
  [{:keys [id] :as resource} request]
  (let [can-manage? (a/can-manage? resource request)
        ops         (cond-> []
                            (a/can-edit? resource request) (conj (u/operation-map id :edit))
                            (a/can-delete? resource request) (conj (u/operation-map id :delete))
                            can-manage? (conj (u/action-map id :check)))]
    (if (seq ops)
      (assoc resource :operations ops)
      (dissoc resource :operations))))


(defmethod p/set-credential-operations cred-tpl-helm-repo/credential-subtype
  [{:keys [resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (crud/set-standard-collection-operations resource request)
    (set-resource-ops resource request)))


;;
;; initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::cred-helm-repo/schema))


(defn initialize
  []
  (md/register resource-metadata))
