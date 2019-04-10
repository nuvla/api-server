(ns sixsq.nuvla.server.resources.spec.credential-template-api-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::ttl
  (-> (st/spec nat-int?)
      (assoc :name "ttl"
             :json-schema/type "integer"
             :json-schema/display-name "TTL"
             :json-schema/description "Time to Live (TTL) for API key/secret"

             :json-schema/order 20)))


(def credential-template-keys-spec
  {:opt-un [::ttl]})

(def credential-template-create-keys-spec
  {:opt-un [::ttl]})

;; Defines the contents of the api-key CredentialTemplate resource itself.
(s/def ::schema
  (su/only-keys-maps ps/resource-keys-spec
                     credential-template-keys-spec))

;; Defines the contents of the api-key template used in a create resource.
(s/def ::template
  (-> (st/spec (su/only-keys-maps ps/template-keys-spec
                                  credential-template-create-keys-spec))
      (assoc :name "template"
             :json-schema/type "map")))

(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::template]}))
