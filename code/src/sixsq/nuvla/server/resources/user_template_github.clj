(ns sixsq.nuvla.server.resources.user-template-github
  "
Resource that is used to create a user account from a GitHub authentication
workflow.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template-github :as ut-github]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const registration-method "github")


(def ^:const resource-name "Registration with GitHub")


(def ^:const resource-url registration-method)


(def resource-acl {:owners    ["group/nuvla-admin"]
                   :view-data ["group/nuvla-anon"]})

;;
;; resource
;;

(def ^:const resource
  {:method            registration-method
   :instance          registration-method
   :name              "GitHub Registration"
   :description       "Creates a new user through github-registration"
   :resource-metadata (str p/resource-type "-" registration-method)
   :acl               resource-acl})


;;
;; initialization: register this user template
;;

(defn initialize
  []
  (p/register registration-method)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ut-github/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ut-github/schema))


(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
