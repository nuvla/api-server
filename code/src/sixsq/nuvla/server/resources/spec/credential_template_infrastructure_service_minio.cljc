(ns sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service-minio
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ct]
    [sixsq.nuvla.server.resources.spec.credential-template-infrastructure-service :as ct-infra-service]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::access-key
  (-> (st/spec string?)
      (assoc :name "username"
             :json-schema/name "username"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "username"
             :json-schema/description "username for Minio service"
             :json-schema/help "username for Minio service"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::secret-key
  (-> (st/spec string?)
      (assoc :name "password"
             :json-schema/name "password"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "password"
             :json-schema/description "password for Minio service"
             :json-schema/help "password for Minio service"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(def credential-template-keys-spec-opt
  {:opt-un [::access-key
            ::secret-key]})


(def credential-template-keys-spec-req
  {:req-un [::access-key
            ::secret-key]})


;; Defines the contents of the credential-template resource itself.
(s/def ::schema
  (su/only-keys-maps ct/resource-keys-spec
                     ct-infra-service/credential-template-service-keys-spec
                     credential-template-keys-spec-opt))


;; Defines the contents of the credential-template used in a create resource.
(s/def ::template
  (su/only-keys-maps ct/template-keys-spec
                     ct-infra-service/credential-template-service-create-keys-spec
                     credential-template-keys-spec-req))


(s/def ::schema-create
  (su/only-keys-maps ct/create-keys-spec
                     {:req-un [::template]}))
