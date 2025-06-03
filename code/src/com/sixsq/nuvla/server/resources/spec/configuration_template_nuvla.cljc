(ns com.sixsq.nuvla.server.resources.spec.configuration-template-nuvla
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::smtp-username
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "smtp-username"
             :json-schema/display-name "SMTP username"
             :json-schema/description "SMTP username for sending email from server"

             :json-schema/order 20)))


(s/def ::smtp-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "smtp-password"
             :json-schema/display-name "SMTP password"
             :json-schema/description "SMTP password for sending email from server"

             :json-schema/order 21
             :json-schema/sensitive true)))


(s/def ::smtp-host
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "smtp-host"
             :json-schema/display-name "SMTP host"
             :json-schema/description "SMTP host for sending email from server"

             :json-schema/order 22)))


(s/def ::smtp-port
  (-> (st/spec ::core/port)
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

(s/def ::smtp-xoauth2
  (-> (st/spec #{"google"})
      (assoc :name "smtp-xoauth2"
             :json-schema/type "string"
             :json-schema/display-name "xoauth provider"
             :json-schema/description "enable XOAUTH2 authorization mechanism")))

(s/def ::smtp-xoauth2-config
  (-> (st/spec (s/map-of keyword? string?))
      (assoc :name "smtp-xoauth2-options"
             :json-schema/type "map"
             :json-schema/description "SMTP XOAUTH 2 options"
             :json-schema/indexed false)))

(s/def ::support-email
  (-> (st/spec ::core/email)
      (assoc :name "support-email"
             :json-schema/display-name "support email"
             :json-schema/description "email address for support"

             :json-schema/order 26)))


(s/def ::stripe-api-key
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "stripe-api-key"
             :json-schema/display-name "stripe api key"
             :json-schema/description "stripe private api-key to communicate with the api"

             :json-schema/order 27)))


(s/def ::stripe-client-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "stripe-client-id"
             :json-schema/display-name "stripe client id"
             :json-schema/description "stripe client-id to create connected accounts"

             :json-schema/order 28)))


(s/def ::external-vulnerabilities-db
  (-> (st/spec ::core/url)
    (assoc :name "external-vulnerabilities-db"
           :json-schema/display-name "external vulnerabilities db"
           :json-schema/description "Link to external DB where to find the vulnerabilities database to be added to Nuvla"
           :json-schema/order 29)))


(s/def ::conditions-url
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "conditions-url"
             :json-schema/display-name "Terms & conditions url"
             :json-schema/description "Terms & conditions url"

             :json-schema/order 30)))


(s/def ::email-header-img-url
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "email-header-img-url"
             :json-schema/display-name "Email header image url"
             :json-schema/description "Email header image url"

             :json-schema/order 31)))

(s/def ::authorized-redirect-urls
  (-> (st/spec (s/coll-of ::core/nonblank-string :kind vector?))
      (assoc :name "authorized-redirect-urls"
             :json-schema/type "array"
             :json-schema/display-name "List of authorized urls"
             :json-schema/description "List of authorized urls that redirects begin with, this is a very important security setting for production server"

             :json-schema/order 32)))


(def configuration-template-keys-spec
  {:opt-un [::smtp-username
            ::smtp-password
            ::smtp-host
            ::smtp-port
            ::smtp-ssl
            ::smtp-debug
            ::smtp-xoauth2
            ::smtp-xoauth2-config
            ::support-email
            ::stripe-api-key
            ::stripe-client-id
            ::external-vulnerabilities-db
            ::conditions-url
            ::email-header-img-url
            ::authorized-redirect-urls]})


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
