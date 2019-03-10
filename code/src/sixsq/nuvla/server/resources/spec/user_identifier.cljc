(ns sixsq.nuvla.server.resources.spec.user-identifier
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::identifier
  (-> (st/spec string?)
      (assoc :name "identifier"
             :json-schema/name "identifier"
             :json-schema/type "string"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "identifier"
             :json-schema/description "identifier to associate with a user"
             :json-schema/help "unique (external) identifier to associate with a user"
             :json-schema/group "body"
             :json-schema/order 10
             :json-schema/hidden false
             :json-schema/sensitive false)))

;; Less restrictive than standard ::cimi-common/id to accommodate OIDC, etc.
(s/def ::userid (s/and string? #(re-matches #"^user/.*" %)))

(s/def ::href ::userid)
(s/def ::resource-link (s/keys :req-un [::href]))

(s/def ::user
  (-> (st/spec ::resource-link)
      (assoc :name "user"
             :json-schema/name "user"
             :json-schema/type "ref"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "user"
             :json-schema/description "id of user resource"
             :json-schema/help "id of user resource associated with the linked identifier"
             :json-schema/group "body"
             :json-schema/order 11
             :json-schema/hidden false
             :json-schema/sensitive false)))

(s/def ::schema
  (su/only-keys-maps c/common-attrs
                     {:req-un [::identifier ::user]}))
