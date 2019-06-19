(ns sixsq.nuvla.server.resources.spec.user
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [sixsq.nuvla.server.resources.spec.credential :as cred-spec]
    [spec-tools.core :as st]))

;; provide tighter definition of user id to be used elsewhere

(def ^:const user-id-regex #"^user/[0-9a-f]{8}\-[0-9a-f]{4}\-[0-9a-f]{4}\-[0-9a-f]{4}\-[0-9a-f]{12}$")

(defn user-id? [s] (re-matches user-id-regex s))

(s/def ::id
  (-> (st/spec (s/and string? user-id?))
      (assoc :name "id"
             :json-schema/type "resource-id"
             :json-schema/description "identifier of user resource"

             :json-schema/order 34)))


(s/def ::method
  (-> (st/spec ::core/identifier)
      (assoc :name "method"
             :json-schema/description "user creation method"

             :json-schema/editable false
             :json-schema/order 30)))


(s/def ::state
  (-> (st/spec #{"NEW" "ACTIVE" "SUSPENDED"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "state of user's account"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 31)))


(def ^:const email-id-regex #"^email/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")

(defn email-id? [s] (re-matches email-id-regex s))

(s/def ::email
  (-> (st/spec (s/and string? email-id?))
      (assoc :name "email"
             :json-schema/type "resource-id"
             :json-schema/description "identifier of primary email address resource"

             :json-schema/order 32)))


(s/def ::credential-password
  (-> cred-spec/credential-id-spec
      (assoc :name "credential-password"
             :json-schema/type "resource-id"
             :json-schema/description "identifier of password credential"

             :json-schema/order 33)))


(def user-keys-spec
  {:req-un [::state]
   :opt-un [::method
            ::credential-password
            ::email]})


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     user-keys-spec))
