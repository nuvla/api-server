(ns sixsq.nuvla.server.resources.infrastructure-service-template-generic
  "
Template that requires all the core attributes of an infrastructure-service
resource.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const method "generic")


(def template {:method           method

               :name             "generic service template"
               :description      "template requiring basic service resource attributes"
               :resource-metadata (str "resource-metadata/" infra-service-tpl/resource-type "-" method)

               :type             "my-service"
               :endpoint         "https://service.example.org:1234"
               :state            "STARTED"

               :acl              infra-service-tpl/resource-acl})


;;
;; initialization: register this template and provide metadata description
;;

(defn initialize
  []
  (infra-service-tpl/register template)
  (md/register (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::infra-service-tpl-generic/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::infra-service-tpl-generic/schema))


(defmethod infra-service-tpl/validate-subtype method
  [resource]
  (validate-fn resource))
