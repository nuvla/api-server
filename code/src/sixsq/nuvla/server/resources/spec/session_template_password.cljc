(ns sixsq.nuvla.server.resources.spec.session-template-password
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.session-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::username
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "username"
             :json-schema/name "username"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "username"
             :json-schema/description "username for your account"
             :json-schema/help "username for your account"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::password
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "password"
             :json-schema/name "password"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "password"
             :json-schema/description "password for your account"
             :json-schema/help "password for your account"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


;; all parameters must be specified in both the template and the create resource
(def session-template-keys-spec-req
  {:req-un [::username ::password]})

;; Defines the contents of the password session-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     session-template-keys-spec-req))

;; Defines the contents of the password template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     session-template-keys-spec-req))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
