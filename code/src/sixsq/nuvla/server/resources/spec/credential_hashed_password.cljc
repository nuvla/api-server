(ns sixsq.nuvla.server.resources.spec.credential-hashed-password
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.resources.spec.credential :as cred]
    [sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::hash
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "hash"
             :json-schema/name "hash"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory false
             :json-schema/mutable false
             :json-schema/consumer-writable false

             :json-schema/display-name "hash"
             :json-schema/description "hashed password"
             :json-schema/help "hashed password"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden true
             :json-schema/sensitive true)))


(def credential-keys-spec
  {:req-un [::hash]})


(s/def ::schema
  (su/only-keys-maps cred/credential-keys-spec
                     credential-keys-spec))
