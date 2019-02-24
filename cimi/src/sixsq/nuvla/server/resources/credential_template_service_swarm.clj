(ns sixsq.nuvla.server.resources.credential-template-service-swarm
  "
This credential-template creates a credential for a Docker Swarm service.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-template :as p]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.credential-template-service-swarm :as tpl-swarm]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const credential-type "service-swarm")


(def ^:const method "service-swarm")


(def ^:const template-acl {:owner {:principal "ADMIN"
                                   :type      "ROLE"}
                           :rules [{:principal "USER"
                                    :type      "ROLE"
                                    :right     "VIEW"}]})


(def ^:const template {:id            (str p/resource-type "/" method)
                       :resource-type p/resource-type
                       :acl           template-acl

                       :type          credential-type
                       :method        method

                       :services      [{:href "service/service-example-1"}
                                       {:href "service/service-example-2"}]

                       :ca            "ca-public-certificate"
                       :cert          "client-public-certificate"
                       :key           "client-private-certificate"})


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl-swarm/schema))
(defmethod p/validate-subtype method
  [resource]
  (validate-fn resource))


;;
;; initialization: register this credential-template
;;

(defn initialize
  []
  (p/register template)
  (md/register (gen-md/generate-metadata ::ns ::p/ns ::tpl-swarm/schema)))
