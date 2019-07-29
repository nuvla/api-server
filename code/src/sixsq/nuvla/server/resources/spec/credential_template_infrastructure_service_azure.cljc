(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-azure
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::azure-client-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "azure-client-id"
             :json-schema/type "string"

             :json-schema/description "Azure client ID"
             :json-schema/order 20)))


(s/def ::azure-client-secret
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "azure-client-secret"
             :json-schema/type "string"

             :json-schema/description "Azure client secret"
             :json-schema/order 21
             :json-schema/sensitive true)))


(s/def ::azure-subscription-id
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "azure-subscription-id"
             :json-schema/type "string"

             :json-schema/description "Azure subscription ID"
             :json-schema/order 22)))


(def credential-template-keys-spec
  {:req-un [::azure-subscription-id
            ::azure-client-secret
            ::azure-client-id]})

(def credential-template-create-keys-spec
  {:req-un [::azure-subscription-id
            ::azure-client-secret
            ::azure-client-id]})

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
