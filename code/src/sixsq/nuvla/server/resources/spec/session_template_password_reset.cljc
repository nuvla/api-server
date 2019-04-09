(ns sixsq.nuvla.server.resources.spec.session-template-password-reset
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.session-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::username
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "username"
             :json-schema/description "username for your account"

             :json-schema/order 20)))


(s/def ::new-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "password"
             :json-schema/description "password for your account"

             :json-schema/order 21
             :json-schema/sensitive true)))


;; all parameters must be specified in both the template and the create resource
(def session-template-keys-spec-req
  {:req-un [::username ::new-password]})


;; Defines the contents of the password session-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     session-template-keys-spec-req))


;; Defines the contents of the password template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  session-template-keys-spec-req))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
