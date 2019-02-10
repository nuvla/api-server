(ns sixsq.nuvla.server.resources.user-template-self-registration
  "
Resource that is used to auto-create a user account given the minimal
information (username, password, and email address) from the user.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template-self-registration :as ut-auto]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


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
   :resourceMetadata (str p/resource-type "-" registration-method)
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
