(ns sixsq.nuvla.server.resources.user-template-email-invitation
  "
Template that allows a user to invite another person with an email address to
use a Nuvla service.
"
  (:require
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template-email-invitation :as spec-email-invitation]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const registration-method "email-invitation")


(def ^:const resource-name "Invite a another person with Email")


(def ^:const resource-url registration-method)


(def resource-acl {:owners   ["group/nuvla-admin"]
                   :view-acl ["group/nuvla-user"]})

;;
;; resource
;;

(def ^:const resource
  {:method            registration-method
   :instance          registration-method
   :name              "Invite another person with Email"
   :description       "allows user to invite another person with email"
   :group             "Invitation with Email"
   :resource-metadata (str "resource-metadata/" p/resource-type "-" registration-method)
   :order             0
   :icon              "user"
   :acl               resource-acl})


;;
;; initialization: register this user template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::spec-email-invitation/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::spec-email-invitation/schema-create "create"))


(defn add-resource []
  (std-crud/add-if-absent
    (str p/resource-type "/" registration-method)
    p/resource-type
    resource))


(defn initialize-data
  []
  (add-resource))


(defn initialize
  []
  (p/register registration-method)
  (std-crud/initialize p/resource-type ::spec-email-invitation/schema)

  (md/register resource-metadata)
  (md/register resource-metadata-create)

  (initialize-data))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::spec-email-invitation/schema))


(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
