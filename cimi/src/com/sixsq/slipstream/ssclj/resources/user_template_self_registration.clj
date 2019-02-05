(ns com.sixsq.slipstream.ssclj.resources.user-template-self-registration
  "
Resource that is used to auto-create a user account given the minimal
information (username, password, and email address) from the user.
"
  (:require
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.resource-metadata :as md]
    [com.sixsq.slipstream.ssclj.resources.spec.user-template-self-registration :as ut-auto]
    [com.sixsq.slipstream.ssclj.resources.user-template :as p]
    [com.sixsq.slipstream.ssclj.util.metadata :as gen-md]))


(def ^:const registration-method "self-registration")


(def ^:const resource-name "Self Registration")


(def ^:const resource-url registration-method)


(def resource-acl {:owner {:principal "ADMIN"
                           :type      "ROLE"}
                   :rules [{:principal "ANON"
                            :type      "ROLE"
                            :right     "VIEW"}]})

;;
;; resource
;;

(def ^:const resource
  {:method           registration-method
   :instance         registration-method
   :name             "Self Registration"
   :description      "Creates a new user through self-registration"
   :resourceMetadata (str p/resource-url "-" registration-method)
   :username         "username"
   :password         "password"
   :passwordRepeat   "password"
   :emailAddress     "user@example.com"
   :acl              resource-acl})


;;
;; initialization: register this User template
;;

(defn initialize
  []
  (p/register registration-method)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ut-auto/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ut-auto/schema))
(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
