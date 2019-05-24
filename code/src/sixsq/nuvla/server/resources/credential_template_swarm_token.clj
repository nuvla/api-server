(ns sixsq.nuvla.server.resources.credential-template-swarm-token
  "
Stores a Swarm token for a master or worker node.
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-swarm-token :as swarm-token]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "swarm-token")


(def ^:const resource-url credential-subtype)


(def ^:const method "swarm-token")


(def resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                            :view-acl ["group/nuvla-user"]}))

;;
;; resource
;;

(def ^:const template
  {:subtype           credential-subtype
   :method            method
   :name              "Swarm Token"
   :description       "stores a Swarm token for master or worker"
   :acl               resource-acl
   :resource-metadata "resource-metadata/credential-template-swarm-token"})


;;
;; initialization: register this credential-template
;;

(defn initialize
  []
  (p/register template)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::swarm-token/schema))
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::swarm-token/schema-create "create")))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::swarm-token/schema))


(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


