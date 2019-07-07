(ns sixsq.nuvla.server.resources.infrastructure-service-template-swarm
  "
Template that requires the information necessary to create and manage a new
Docker Swarm cluster on a given cloud infrastructure.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm :as tpl-swarm]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const method "swarm")


(def template {:method             method

               :name               "create swarm service template"
               :description        "template to create docker swarm"
               :resource-metadata  (str "resource-metadata/" infra-service-tpl/resource-type "-" method)

               :subtype            "swarm"
               :service-credential {:href "credential/change-me"}

               :acl                infra-service-tpl/resource-acl})


;;
;; initialization: register this template and provide metadata description
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::tpl-swarm/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::tpl-swarm/schema-create "create"))


(defn initialize
  []
  (infra-service-tpl/register template)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl-swarm/schema))


(defmethod infra-service-tpl/validate-subtype method
  [resource]
  (validate-fn resource))
