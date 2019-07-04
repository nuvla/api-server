(ns sixsq.nuvla.server.resources.credential-template-hashed-password
  "
Takes a plain-text password and then creates a credential containing a hash of
that password. The credential provides actions for validating the password or
changing it.
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-hashed-password :as hashed-password]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "hashed-password")


(def ^:const resource-url credential-subtype)


(def ^:const method "generate-hashed-password")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const template
  {:subtype           credential-subtype
   :method            method
   :name              "Hashed Password"
   :description       "stores hashed value of a password"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-hashed-password"})


;;
;; initialization: register this credential-template
;;

(defn initialize
  []
  (p/register template)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::hashed-password/schema))
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::hashed-password/schema-create "create")))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::hashed-password/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


