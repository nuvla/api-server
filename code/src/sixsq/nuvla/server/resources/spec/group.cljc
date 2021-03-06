(ns sixsq.nuvla.server.resources.spec.group
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;; FIXME: Make a general macro for identifiers with a fixed prefix.
(def user-id-regex #"^user/[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}$")


(s/def ::user-id
  (-> (st/spec (s/and string? #(re-matches user-id-regex %)))
      (assoc :name "user-id"
             :json-schema/type "string"
             :json-schema/display-name "user ID"
             :json-schema/description "user identifier")))


(s/def ::users
  (-> (st/spec (s/coll-of ::user-id :kind vector? :distinct true))
      (assoc :name "users"
             :json-schema/type "array"
             :json-schema/description "list of users in this group"

             :json-schema/order 20)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::users]}))
