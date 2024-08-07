(ns com.sixsq.nuvla.server.resources.infrastructure-service-template-generic
  "
Template that requires all the core attributes of a generic
`infrastructure-service` resource.
"
  (:require
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const method "generic")


(def template {:method            method

               :name              "generic service template"
               :description       "template requiring basic service resource attributes"
               :resource-metadata (str "resource-metadata/" infra-service-tpl/resource-type "-" method)

               :subtype           "my-service"
               :endpoint          "https://service.example.org:1234"
               :state             "STARTED"

               :acl               infra-service-tpl/resource-acl})


;;
;; initialization: register this template and provide metadata description
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::infra-service-tpl-generic/schema))


(def resource-metadata-create (gen-md/generate-metadata ::ns ::infra-service-tpl/ns ::infra-service-tpl-generic/schema-create "create"))


(defn initialize
  []
  (infra-service-tpl/register template)
  (md/register resource-metadata)
  (md/register resource-metadata-create))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::infra-service-tpl-generic/schema))


(defmethod infra-service-tpl/validate-subtype method
  [resource]
  (validate-fn resource))
