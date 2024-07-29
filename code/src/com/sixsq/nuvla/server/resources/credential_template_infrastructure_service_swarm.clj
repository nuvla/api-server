(ns com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm
  "
This credential-template creates a credential for a Docker Swarm service.
These credentials include a certificate authority's public certificate ('ca'),
the user's public certificate ('cert'), and the user's private key ('key').
"
  (:require
    [com.sixsq.nuvla.auth.utils.acl :as acl-utils]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential-template :as p]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-coe :as ct-infra-service-coe]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-subtype "infrastructure-service-swarm")


(def ^:const method "infrastructure-service-swarm")


(def ^:const resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                                    :view-acl ["group/nuvla-user"]}))


;; No reasonable defaults for :parent, :ca, :cert, :key.
;; Do not provide values for those in the template
(def ^:const template {:id            (str p/resource-type "/" method)
                       :resource-type p/resource-type
                       :acl           resource-acl

                       :subtype       credential-subtype
                       :method        method

                       :ca            "ca-public-certificate"
                       :cert          "client-public-certificate"
                       :key           "client-private-certificate"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-infra-service-coe/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this credential-template
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::p/ns ::ct-infra-service-coe/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::p/ns ::ct-infra-service-coe/schema-create "create"))


(defn initialize
  []
  (p/register template)
  (md/register resource-metadata)
  (md/register resource-metadata-create))
