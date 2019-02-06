(ns sixsq.nuvla.server.resources.spec.credential-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; All credential templates must indicate the type of credential to create.
(s/def ::type
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "type"
             :json-schema/name "type"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "type"
             :json-schema/description "type of credential"
             :json-schema/help "type of credential"
             :json-schema/group "body"
             :json-schema/order 0
             :json-schema/hidden true
             :json-schema/sensitive false)))


;; A given credential may have more than one method for creating it.  All
;; credential templates must provide a method name.
(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/name "method"
             :json-schema/namespace common-ns/slipstream-namespace
             :json-schema/uri common-ns/slipstream-uri
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "method"
             :json-schema/description "method for creating credential"
             :json-schema/help "method for creating credential"
             :json-schema/group "body"
             :json-schema/order 1
             :json-schema/hidden true
             :json-schema/sensitive false)))


(def credential-template-regex #"^credential-template/[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$")
(s/def :cimi.credential-template/href (s/and string? #(re-matches credential-template-regex %)))


;;
;; Keys specifications for CredentialTemplate resources.
;; As this is a "base class" for CredentialTemplate resources, there
;; is no sense in defining map resources for the resource itself.
;;

(def credential-template-keys-spec {:req-un [::type
                                             ::method]})

(def credential-template-keys-spec-opt {:opt-un [::type
                                                 ::method]})

(def resource-keys-spec

  (su/merge-keys-specs [c/common-attrs
                        credential-template-keys-spec]))

;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))

(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))

;; subclasses MUST provide the href to the template to use
(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        credential-template-keys-spec-opt]))

