(ns sixsq.nuvla.server.resources.spec.user
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as cimi-common]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(def ^:const credential-id-regex #"^credential/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn credential-id? [s] (re-matches credential-id-regex s))
(s/def ::credential-password (s/and string? credential-id?))

(def ^:const email-id-regex #"^email/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")
(defn email-id? [s] (re-matches email-id-regex s))
(s/def ::email (s/and string? email-id?))


(s/def ::state
  (-> (st/spec #{"NEW" "ACTIVE" "SUSPENDED"})
      (assoc :name "state"
             :json-schema/name "state"
             :json-schema/type "string"
             :json-schema/providerMandatory false
             :json-schema/consumerMandatory false
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "state"
             :json-schema/description "state of user's account"
             :json-schema/help "state of user's account"
             :json-schema/group "body"
             :json-schema/order 34
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::method
  (-> (st/spec ::cimi-core/identifier)
      (assoc :name "method"
             :json-schema/name "method"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "method"
             :json-schema/description "user creation method"
             :json-schema/help "user creation method"
             :json-schema/group "body"
             :json-schema/order 50
             :json-schema/hidden false
             :json-schema/sensitive false)))


(def user-keys-spec
  {:req-un [::state]
   :opt-un [::method
            ::credential-password
            ::email]})


(s/def ::schema
  (su/only-keys-maps cimi-common/common-attrs
                     user-keys-spec))
