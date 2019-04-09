(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-kubernetes
  "
This template requires the parameters necessary to create a new Kubernetes
on a cloud infrastructure.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::service-credential
  (-> (st/spec ::cimi-common/resource-link)
      (assoc :name "service-credential"
             :json-schema/display-name "service credential"
             :json-schema/description "reference to service credential"

             :json-schema/editable false
             :json-schema/order 22
             :json-schema/hidden true)))


(def service-template-keys-spec
  {:req-un [::service-credential]})


;; Defines the contents of the this service-template resource.
(s/def ::schema
  (su/only-keys-maps infra-service-tpl/resource-keys-spec
                     service-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps infra-service-tpl/template-keys-spec
                                  service-template-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps infra-service-tpl/create-keys-spec
                     {:req-un [::template]}))
