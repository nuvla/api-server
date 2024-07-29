(ns com.sixsq.nuvla.server.resources.spec.credential-hashed-password
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as ps]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::hash
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "hash"
             :json-schema/description "hashed password"

             :json-schema/order 20
             :json-schema/editable false
             :json-schema/hidden true
             :json-schema/sensitive true)))


(def credential-keys-spec
  {:req-un [::hash]})


(s/def ::schema
  (su/only-keys-maps ps/credential-keys-spec
                     credential-keys-spec))
