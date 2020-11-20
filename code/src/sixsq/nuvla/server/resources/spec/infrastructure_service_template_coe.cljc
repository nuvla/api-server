(ns sixsq.nuvla.server.resources.spec.infrastructure-service-template-coe
  "
This template requires the parameters necessary to create a new COE
on a cloud infrastructure.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-coe :as infra-service-coe]
    [sixsq.nuvla.server.resources.spec.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def service-template-keys-spec
  {:req-un [::infra-service-coe/management-credential]
   :opt-un [::infra-service-coe/cluster-params]})


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
