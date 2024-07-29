(ns com.sixsq.nuvla.server.resources.spec.configuration-template-session-github
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.configuration-template :as ps]
    [com.sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::client-id
  (-> (st/spec ::cimi-core/token)
      (assoc :name "client-id"
             :json-schema/displayName "client ID"
             :json-schema/description "GitHub client ID associated with registered application"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::client-secret
  (-> (st/spec ::cimi-core/token)
      (assoc :name "client-secret"
             :json-schema/displayName "client secret"
             :json-schema/description "GitHub client secret associated with registered application"
             :json-schema/group "body"
             :json-schema/order 21
             :json-schema/hidden false
             :json-schema/sensitive true)))


(s/def ::instance (st/spec ::ps/instance))


(def configuration-template-keys-spec-req
  {:req-un [::instance ::client-id ::client-secret]})

(def configuration-template-keys-spec-create
  {:req-un [::instance ::client-id ::client-secret]})

;; Defines the contents of the github authentication configuration-template resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     configuration-template-keys-spec-req))

;; Defines the contents of the github authentication template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     configuration-template-keys-spec-create))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
