(ns sixsq.nuvla.server.resources.spec.user-template-email-invitation
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.user-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::email
  (-> (st/spec ::core/email)
      (assoc :name "email"
             :json-schema/display-name "email address"
             :json-schema/description "email address of the invited person"

             :json-schema/order 21)))


;; no good defaults for these keys, make them optional in template
(def user-template-email-invitation-keys-opt
  {:opt-un [::email]})


;; expanded template must have these keys defined
(def user-template-email-invitation-keys-req
  {:req-un [::email]})


(def user-template-email-invitation-keys-href
  {:opt-un [::ps/href]})


;; Defines the contents of the password user-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     user-template-email-invitation-keys-opt))


;; Defines the contents of the password template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  user-template-email-invitation-keys-req
                                  user-template-email-invitation-keys-href))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
