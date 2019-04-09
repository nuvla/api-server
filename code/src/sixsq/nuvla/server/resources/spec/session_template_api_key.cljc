(ns sixsq.nuvla.server.resources.spec.session-template-api-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.session-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::key
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "key"
             :json-schema/name "key"
             :json-schema/display-name "key"
             :json-schema/description "key for API key/secret pair"

             :json-schema/order 20)))


(s/def ::secret
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "secret"
             :json-schema/name "secret"
             :json-schema/display-name "secret"
             :json-schema/description "secret for API key/secret pair"

             :json-schema/order 21
             :json-schema/sensitive true)))


;; all parameters must be specified in both the template and the create resource
(def session-template-keys-spec
  {:req-un [::key ::secret]})

;; Defines the contents of the api-key SessionTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     session-template-keys-spec))

;; Defines the contents of the api-key template used in a create resource.
(s/def ::template
  (su/only-keys-maps ps/template-keys-spec
                     session-template-keys-spec))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
