(ns sixsq.nuvla.server.resources.spec.service-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::method
  (-> (st/spec ::cimi-core/kebab-identifier)
      (assoc :name "method"
             :json-schema/name "method"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable false
             :json-schema/templateMutable true

             :json-schema/displayName "method"
             :json-schema/description "service creation method"
             :json-schema/help "service creation method"
             :json-schema/group "body"
             :json-schema/order 0
             :json-schema/hidden true
             :json-schema/sensitive false)))


;; Restrict the href used to create services.
(def service-template-regex #"^service-template/[a-z]+(-[a-z]+)*$")
(s/def ::href (s/and string? #(re-matches service-template-regex %)))

;;
;; Keys specifications for service-template resources.
;; As this is a "base class" for service-template resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def service-template-keys-spec {:req-un [::method]})


(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        service-template-keys-spec]))


;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))


(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))


(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        service-template-keys-spec
                        {:req-un [::href]}]))
