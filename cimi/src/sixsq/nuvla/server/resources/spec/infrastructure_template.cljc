(ns sixsq.nuvla.server.resources.spec.infrastructure-template
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.common-namespaces :as common-ns]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.ui-hints :as hints]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; All infrastructure resources should mention the type
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
             :json-schema/consumerWritable false
             :json-schema/templateMutable true

             :json-schema/displayName "type"
             :json-schema/description "type of infrastructure"
             :json-schema/help "type of infrastructure"
             :json-schema/group "body"
             :json-schema/order 0
             :json-schema/hidden true
             :json-schema/sensitive false)))


;; Restrict the href used to create infrastructures.
(def infrastructure-template-regex #"^infrastructure-template/[a-z]+(-[a-z]+)*$")
(s/def ::href (s/and string? #(re-matches infrastructure-template-regex %)))


(def infrastructure-template-keys-spec {:req-un [::type]})


(def resource-keys-spec
  (su/merge-keys-specs [c/common-attrs
                        hints/ui-hints-spec
                        infrastructure-template-keys-spec]))


;; Used only to provide metadata resource for collection.
(s/def ::schema
  (su/only-keys-maps resource-keys-spec))


(def create-keys-spec
  (su/merge-keys-specs [c/create-attrs]))


(def template-keys-spec
  (su/merge-keys-specs [c/template-attrs
                        hints/ui-hints-spec
                        infrastructure-template-keys-spec
                        {:req-un [::href]}]))

