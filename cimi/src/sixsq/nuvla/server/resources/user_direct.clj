(ns sixsq.nuvla.server.resources.user-direct
  (:require
    [sixsq.nuvla.auth.internal :as ia]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.spec.user]
    [sixsq.nuvla.server.resources.spec.user-template-direct :as ut-direct]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-direct :as tpl]))

;;
;; validate the create resource
;;

(def create-validate-fn (u/create-spec-validation-fn ::ut-direct/schema-create))
(defmethod p/create-validate-subtype tpl/registration-method
  [resource]
  (create-validate-fn resource))

;;
;; transform template into user resource
;; just updates the resource URI and sets isSuperUser if not set
;;
(defmethod p/tpl->user tpl/registration-method
  [{:keys [isSuperUser] :as resource} request]
  [nil (cond-> (assoc resource :resourceURI p/resource-uri)
               true (dissoc :instance :group :order :icon :hidden)
               (nil? isSuperUser) (assoc :isSuperUser false))])
