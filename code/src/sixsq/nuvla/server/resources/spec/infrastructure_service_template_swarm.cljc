(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-swarm
  "
This template requires the parameters necessary to create a new Docker Swarm
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
             :json-schema/name "service-credential"
             :json-schema/required true
             :json-schema/editable false

             :json-schema/display-name "service credential"
             :json-schema/description "reference to service credential"
             :json-schema/help "reference to service credential to use to create Docker Swarm"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden true
             :json-schema/sensitive false)))


(def service-template-keys-spec
  {:req-un [::service-credential]})


;; Defines the contents of the this service-template resource.
(s/def ::schema
  (su/only-keys-maps infra-service-tpl/resource-keys-spec
                     service-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (su/only-keys-maps infra-service-tpl/template-keys-spec
                     service-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps infra-service-tpl/create-keys-spec
                     {:req-un [::template]}))
