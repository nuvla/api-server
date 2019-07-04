(ns sixsq.nuvla.server.resources.infrastructure-service-template-kubernetes
  "
Template that requires the information necessary to create and manage a new
Kubernetes cluster on a given cloud infrastructure.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-kubernetes :as tpl-kubernetes]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const method "kubernetes")


(def template {:method             method

               :name               "create swarm service template"
               :description        "template to create kubernetes"
               :resource-metadata  (str "resource-metadata/" infra-service-tpl/resource-type "-" method)

               :subtype            "kubernetes"
               :service-credential {:href "credential/change-me"}

               :acl                infra-service-tpl/resource-acl})


;;
;; initialization: register this template and provide metadata description
;;

(defn initialize
  []
  (infra-service-tpl/register template)
  (md/register (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::tpl-kubernetes/schema))
  (md/register (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::tpl-kubernetes/schema-create "create")))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl-kubernetes/schema))


(defmethod infra-service-tpl/validate-subtype method
  [resource]
  (validate-fn resource))
