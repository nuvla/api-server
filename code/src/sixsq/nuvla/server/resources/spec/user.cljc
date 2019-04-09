(ns sixsq.nuvla.server.resources.spec.user
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

;; provide tighter definition of user id to be used elsewhere
(def ^:const user-id-regex #"^user/[0-9a-f]{8}\-[0-9a-f]{4}\-[0-9a-f]{4}\-[0-9a-f]{4}\-[0-9a-f]{12}$")
(defn user-id? [s] (re-matches user-id-regex s))
(s/def ::id (s/and string? user-id?))


(def ^:const credential-id-regex #"^credential/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn credential-id? [s] (re-matches credential-id-regex s))
(s/def ::credential-password (s/and string? credential-id?))


(def ^:const email-id-regex #"^email/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn email-id? [s] (re-matches email-id-regex s))
(s/def ::email (s/and string? email-id?))


(s/def ::state
  (-> (st/spec #{"NEW" "ACTIVE" "SUSPENDED"})
      (assoc :name "state"
             :json-schema/type "string"
             :json-schema/description "state of user's account"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 34)))


(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/description "user creation method"

             :json-schema/order 50)))


(def user-keys-spec
  {:req-un [::state]
   :opt-un [::method
            ::credential-password
            ::email]})


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     user-keys-spec))
