(ns sixsq.nuvla.server.resources.service-template-generic
  "
Template that requires all the core attributes of a service resource.
"
  (:require
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.service-template :as tpl]
    [sixsq.nuvla.server.resources.spec.service-template-generic :as tpl-generic]
    [sixsq.nuvla.server.util.metadata :as gen-md]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const method "generic")


(def template {:method           method

               :name             "generic service template"
               :description      "template requiring basic service resource attributes"
               :resourceMetadata (str "resource-metadata/" tpl/resource-type "-" method)

               :type             "my-service"
               :endpoint         "https://service.example.org:1234"
               :state            "STARTED"

               :acl              tpl/resource-acl})


;;
;; initialization: register this template and provide metadata description
;;

(defn initialize
  []
  (tpl/register template)
  (md/register (gen-md/generate-metadata ::ns ::tpl/ns ::tpl-generic/schema)))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::tpl-generic/schema))


(defmethod tpl/validate-subtype method
  [resource]
  (validate-fn resource))
