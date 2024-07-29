(ns com.sixsq.nuvla.server.resources.spec.user-template-minimum
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.user-template :as ps]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::username
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "username"
             :json-schema/description "username"

             :json-schema/order 20)))


(s/def ::email
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "email"
             :json-schema/description "email address"

             :json-schema/order 21)))


(s/def ::password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "password"
             :json-schema/description "password for your account"

             :json-schema/order 22
             :json-schema/sensitive true)))


(def keys-opt
  {:opt-un [::username
            ::email
            ::password]})


(def keys-href
  {:opt-un [::ps/href]})


;; Defines the contents of the password user-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     keys-opt))


;; Defines the contents of the password template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  keys-opt
                                  keys-href))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
