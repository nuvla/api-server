(ns sixsq.nuvla.server.resources.spec.configuration-template-nuvla
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::smtp-username
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "smtp-username"
             :json-schema/name "smtp-username"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "SMTP username"
             :json-schema/description "SMTP username for sending email from server"
             :json-schema/help "SMTP username for sending email from server"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::smtp-password
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "smtp-password"
             :json-schema/name "smtp-password"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "SMTP password"
             :json-schema/description "SMTP password for sending email from server"
             :json-schema/help "SMTP password for sending email from server"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::smtp-host
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "smtp-host"
             :json-schema/name "smtp-host"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "SMTP host"
             :json-schema/description "SMTP host for sending email from server"
             :json-schema/help "SMTP host for sending email from server"
             :json-schema/group "body"
             :json-schema/order 22
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::smtp-port
  (-> (st/spec ::cimi-core/port)
      (assoc :name "smtp-port"
             :json-schema/name "smtp-port"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "SMTP port"
             :json-schema/description "SMTP port for sending email from server"
             :json-schema/help "SMTP port for sending email from server"
             :json-schema/group "body"
             :json-schema/order 23
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::smtp-ssl
  (-> (st/spec boolean?)
      (assoc :name "smtp-ssl"
             :json-schema/name "smtp-ssl"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "SMTP SSL?"
             :json-schema/description "use SSL when interacting with SMTP server?"
             :json-schema/help "use SSL when interacting with SMTP server?"
             :json-schema/group "body"
             :json-schema/order 24
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::smtp-debug
  (-> (st/spec boolean?)
      (assoc :name "smtp-debug"
             :json-schema/name "smtp-debug"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "debug SMTP?"
             :json-schema/description "turn on debugging when interacting with SMTP server?"
             :json-schema/help "turn on debugging when interacting with SMTP server?"
             :json-schema/group "body"
             :json-schema/order 25
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::support-email
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "support-email"
             :json-schema/name "support-email"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "support email"
             :json-schema/description "email address for support"
             :json-schema/help "email address for support"
             :json-schema/group "body"
             :json-schema/order 26
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def configuration-template-keys-spec
  {:opt-un [::smtp-username
            ::smtp-password
            ::smtp-host
            ::smtp-port
            ::smtp-ssl
            ::smtp-debug
            ::support-email]})


;; Defines the contents of the nuvla configuration-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec))


;; Defines the contents of the nuvla template key used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [::template]}))
