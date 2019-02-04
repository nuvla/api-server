(ns com.sixsq.slipstream.ssclj.resources.user-direct
  (:require
    [com.sixsq.slipstream.auth.internal :as ia]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.spec.user]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-direct :as ut-direct]
    [com.sixsq.slipstream.ssclj.resources.user :as p]
    [com.sixsq.slipstream.ssclj.resources.user-template-direct :as tpl]))

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
