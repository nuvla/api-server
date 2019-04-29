(ns sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm
  "
This credential-template creates a credential for a Docker Swarm service.
These credentials include a certificate authority's public certificate ('ca'),
the user's public certificate ('cert'), and the user's private key ('key').
"
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-swarm :as ct-infra-service-swarm]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type "infrastructure-service-swarm")


(def ^:const method "infrastructure-service-swarm")


(def ^:const resource-acl (acl-utils/normalize-acl {:owners   ["group/nuvla-admin"]
                                                    :view-acl ["group/nuvla-user"]}))


;; No reasonable defaults for :infrastructure-services, :ca, :cert, :key.
;; Do not provide values for those in the template
(def ^:const template {:id                      (str p/resource-type "/" method)
                       :resource-type           p/resource-type
                       :acl                     resource-acl

                       :type                    credential-type
                       :method                  method

                       :infrastructure-services ["infrastructure-service/service-example-1"
                                                 "infrastructure-service/service-example-2"]

                       :ca                      "ca-public-certificate"
                       :cert                    "client-public-certificate"
                       :key                     "client-private-certificate"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-infra-service-swarm/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this credential-template
;;

(defn initialize
  []
  (p/register template)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ct-infra-service-swarm/schema))
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::ct-infra-service-swarm/schema-create "create")))
