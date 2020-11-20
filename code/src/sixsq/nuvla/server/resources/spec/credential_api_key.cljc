(ns sixsq.nuvla.server.resources.spec.credential-api-key
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.resources.spec.credential-template-api-key :as api-key]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::digest
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "digest"
             :json-schema/description "digest (hash) of secret key"

             :json-schema/order 20)))


(s/def ::identity
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "identity"
             :json-schema/description "associated identity")))


(s/def ::role
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "role"
             :json-schema/description "associated role")))


(s/def ::roles
  (-> (st/spec (s/coll-of ::role
                          :kind vector?
                          :into []
                          :min-count 1))
      (assoc :name "roles"
             :json-schema/type "array"
             :json-schema/description "associated roles")))


(s/def ::claims
  (-> (st/spec (su/only-keys :req-un [::identity]
                             :opt-un [::roles]))
      (assoc :name "claims"
             :json-schema/type "map"
             :json-schema/description "associated claims"
             :json-schema/order 21)))


(s/def ::expiry
  (-> (st/spec ::core/timestamp)
      (assoc :name "expiry"
             :json-schema/description "expiry timestamp for API key-secret pair"

             :json-schema/order 22)))


(def credential-keys-spec
  {:req-un [::digest
            ::claims]
   :opt-un [::expiry]})


(s/def ::schema
  (su/only-keys-maps ps/credential-keys-spec
                     credential-keys-spec))


;; multiple methods to create an ssh public key, so multiple schemas
(s/def ::schema-create
  (su/only-keys-maps ps/create-keys-spec
                     {:req-un [::api-key/template]}))
