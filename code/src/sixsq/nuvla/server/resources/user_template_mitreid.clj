(ns sixsq.nuvla.server.resources.user-template-mitreid
  "
Resource that is used to create a user account from the standard OIDC
authentication workflow as implemented by a MITREid server.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.user-template-mitreid :as ut-mitreid]
    [sixsq.nuvla.server.resources.user-template :as p]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const registration-method "mitreid")


(def ^:const resource-name "MITREid")


(def ^:const resource-url registration-method)


(def resource-acl {:owners    ["group/nuvla-admin"]
                   :view-data ["group/nuvla-anon"]})

;;
;; resource
;;

(def ^:const resource
  {:method            registration-method
   :instance          registration-method
   :name              "MITREid Registration"
   :description       "Creates a new user through mitreid-registration"
   :resource-metadata (str p/resource-type "-" registration-method)
   :acl               resource-acl})


;;
;; initialization: register this User template
;;

(defn initialize
  []
  (p/register registration-method)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ut-mitreid/schema))
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ut-mitreid/schema-create "create")))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ut-mitreid/schema))


(defmethod p/validate-subtype registration-method
  [resource]
  (validate-fn resource))
