(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-exoscale
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service :as ct-infra-service]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::exoscale-api-key
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "exoscale-api-key"
             :json-schema/type "string"
             :json-schema/description "API key for Exoscale"
             :json-schema/order 20)))


(s/def ::exoscale-api-secret-key
  (-> (st/spec string?)                                     ;; ::cimi-core/nonblank-string
      (assoc :name "exoscale-api-secret-key"
             :json-schema/type "string"
             :json-schema/description "API secret key for Exoscale"
             :json-schema/order 21
             :json-schema/sensitive true)))


(def credential-template-keys-spec
  {:req-un [::exoscale-api-key
            ::exoscale-api-secret-key]})

(def credential-template-create-keys-spec
  {:req-un [::exoscale-api-key
            ::exoscale-api-secret-key]})

;; Defines the contents of the api-key CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     ct-infra-service/credential-template-service-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the api-key template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ct/template-keys-spec
                                  ct-infra-service/credential-template-service-create-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))

(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
