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
             :json-schema/display-name "SMTP username"
             :json-schema/description "SMTP username for sending email from server"

             :json-schema/order 20)))


(s/def ::smtp-password
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "smtp-password"
             :json-schema/display-name "SMTP password"
             :json-schema/description "SMTP password for sending email from server"

             :json-schema/order 21
             :json-schema/sensitive true)))


(s/def ::smtp-host
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "smtp-host"
             :json-schema/display-name "SMTP host"
             :json-schema/description "SMTP host for sending email from server"

             :json-schema/order 22)))


(s/def ::smtp-port
  (-> (st/spec ::cimi-core/port)
      (assoc :name "smtp-port"
             :json-schema/display-name "SMTP port"
             :json-schema/description "SMTP port for sending email from server"

             :json-schema/order 23)))


(s/def ::smtp-ssl
  (-> (st/spec boolean?)
      (assoc :name "smtp-ssl"
             :json-schema/type "boolean"
             :json-schema/display-name "SMTP SSL?"
             :json-schema/description "use SSL when interacting with SMTP server?"

             :json-schema/order 24)))


(s/def ::smtp-debug
  (-> (st/spec boolean?)
      (assoc :name "smtp-debug"
             :json-schema/type "boolean"
             :json-schema/display-name "debug SMTP?"
             :json-schema/description "turn on debugging when interacting with SMTP server?"

             :json-schema/order 25)))


(s/def ::support-email
  (-> (st/spec ::cimi-core/email)
      (assoc :name "support-email"
             :json-schema/display-name "support email"
             :json-schema/description "email address for support"

             :json-schema/order 26)))


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
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  configuration-template-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))


(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:opt-un [::template]}))
