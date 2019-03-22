(ns sixsq.nuvla.server.resources.spec.group-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; Restrict the href used to create groups.
(def group-template-regex #"^group-template/[a-z][a-z0-9]*(-[a-z0-9]+)*$")
(s/def ::href (s/and string? #(re-matches group-template-regex %)))


(s/def ::group-identifier
  (-> (st/spec ::cimi-core/kebab-identifier)
      (assoc :name "group-identifer"
             :json-schema/name "group-identifer"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "group identifier"
             :json-schema/description "unique kebab-case identifier for group"
             :json-schema/help "unique kebab-case identifier for group"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def group-template-keys-spec-req
  {:req-un [::group-identifier]})


(def group-template-keys-spec-opt
  {:opt-un [::group-identifier]})


;; Defines the contents of the group-template resource itself.
;; No good default for group-identifier, so it is optional.
(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     group-template-keys-spec-opt))


;; Defines the contents of template used in a create resource.
;; This obviously must include the group-identifier.
(s/def ::template
  (su/only-keys-maps common/template-attrs
                     group-template-keys-spec-req
                     {:opt-un [::href]}))


(s/def ::schema-create
  (su/only-keys-maps common/create-attrs
                     {:req-un [::template]}))
