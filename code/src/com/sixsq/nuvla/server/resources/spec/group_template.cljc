(ns com.sixsq.nuvla.server.resources.spec.group-template
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; Restrict the href used to create groups.
(def group-template-regex #"^group-template/[a-z][a-z0-9]*(-[a-z0-9]+)*$")


(s/def ::href
  (-> (st/spec (s/and string? #(re-matches group-template-regex %)))
      (assoc :name "href"
             :json-schema/type "resource-id"
             :json-schema/description "reference to group template identifier"

             :json-schema/order 20)))



(s/def ::group-identifier
  (-> (st/spec ::core/kebab-identifier)
      (assoc :name "group-identifer"
             :json-schema/display-name "group identifier"
             :json-schema/description "unique kebab-case identifier for group"

             :json-schema/order 21)))


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
  (-> (st/spec (su/only-keys-maps common/template-attrs
                                  group-template-keys-spec-req
                                  {:opt-un [::href]}))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps common/create-attrs
                     {:req-un [::template]}))
