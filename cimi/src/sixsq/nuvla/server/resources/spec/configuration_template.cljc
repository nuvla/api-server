(ns sixsq.nuvla.server.resources.spec.configuration-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::service
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "service"
             :json-schema/name "service"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable false
             :json-schema/consumerWritable true

             :json-schema/displayName "service"
             :json-schema/description "name of service associated with this resource"
             :json-schema/help "name of service associated with this resource"
             :json-schema/group "body"
             :json-schema/order 10
             :json-schema/hidden true
             :json-schema/sensitive false)))


(s/def ::instance
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "instance"
             :json-schema/name "instance"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable false
             :json-schema/consumerWritable true

             :json-schema/displayName "instance"
             :json-schema/description "instance of service associated with this resource"
             :json-schema/help "instance of service associated with this resource"
             :json-schema/group "body"
             :json-schema/order 11
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def configuration-template-regex #"^configuration-template/[a-z0-9]+(-[a-z0-9]+)*$")

(s/def ::href
  (-> (st/spec (s/and string? #(re-matches configuration-template-regex %)))
      (assoc :name "href"
             :json-schema/name "href"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable false
             :json-schema/consumerWritable true

             :json-schema/displayName "href"
             :json-schema/description "reference to the configuration template used"
             :json-schema/help "reference to the configuration template used"
             :json-schema/group "body"
             :json-schema/order 12
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::configurationTemplate
  (-> (st/spec (su/only-keys-maps {:req-un [::href]}))
      (assoc :name "configurationTemplate"
             :json-schema/name "configurationTemplate"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "map"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "configurationTemplate"
             :json-schema/description "reference to the configuration template used"
             :json-schema/help "reference to the configuration template used"
             :json-schema/group "body"
             :json-schema/order 13
             :json-schema/hidden false
             :json-schema/sensitive false)))


;;
;; Keys specifications for configuration-template resources.
;; As this is a "base class" for configuration-template resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def configuration-template-keys-spec {:req-un [::service]
                                       :opt-un [::instance ::configurationTemplate]})

(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs configuration-template-keys-spec]))

(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))

(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        configuration-template-keys-spec
                        {:opt-un [::href]}]))

