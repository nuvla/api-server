(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-google
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::google-username
       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
           (assoc :name "google-username"
                  :json-schema/type "string"

                  :json-schema/description "User name associated with the service account"
                  :json-schema/order 20)))


(s/def ::google-project
       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
           (assoc :name "google-project"
                  :json-schema/type "string"

                  :json-schema/description "User project to use for provisoining"
                  :json-schema/order 21)))


(s/def ::client-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "client_id"
             :json-schema/type "string"

             :json-schema/description "Client ID associated with the service account"
             :json-schema/order 22)))


(s/def ::client-secret
       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
           (assoc :name "client_secret"
                  :json-schema/type "string"

                  :json-schema/description "Client secret associated with the service account"
                  :json-schema/order 23)))


(s/def ::refresh-token
       (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
           (assoc :name "refresh_token"
                  :json-schema/type "string"

                  :json-schema/description "Refresh token for the creds."
                  :json-schema/order 24)))


(def credential-template-keys-spec
  {:req-un [::google-username
            ::client-id
            ::client-secret
            ::refresh-token]
   :opt-un [::google-project]})

(def credential-template-create-keys-spec credential-template-keys-spec)

;; Defines the contents of the api-key CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the api-key template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ct/template-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))

(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
