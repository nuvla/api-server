(ns sixsq.nuvla.server.resources.spec.group
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; FIXME: Make a general macro for identifiers with a fixed prefix.
(def user-id-regex #"^user/[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}$")


(s/def ::user-id (s/and string? #(re-matches user-id-regex %)))


(s/def ::users
  (-> (st/spec (s/coll-of ::user-id :kind vector? :distinct true))
      (assoc :name "users"
             :json-schema/name "users"
             :json-schema/type "Array"
             :json-schema/providerMandatory true
             :json-schema/consumerMandatory true
             :json-schema/mutable true
             :json-schema/consumerWritable true

             :json-schema/displayName "users"
             :json-schema/description "list of users in this group"
             :json-schema/help "list of users in this group"
             :json-schema/group "body"
             :json-schema/category "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::schema
  (su/only-keys-maps c/common-attrs
                     {:req-un [::users]}))
