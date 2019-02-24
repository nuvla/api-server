(ns sixsq.nuvla.server.resources.spec.service-template-generic
  "
This generic template for service resources simply requires the service type
and endpoint attributes to be specified. The accessible attribute will default
to true, if not provided. This template is most appropriate for pre-existing
services that are managed separately.
  "
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.service :as service]
    [sixsq.nuvla.server.resources.spec.service-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]))


(def service-template-keys-spec
  {:req-un [::service/type
            ::service/endpoint]
   :opt-un [::service/accessible]})


;; Defines the contents of the this service-template resource.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     service-template-keys-spec))


;; Defines the contents of the template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     service-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
