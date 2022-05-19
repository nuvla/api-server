(ns sixsq.nuvla.server.resources.spec.group
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(def user-id-regex #"^user/[0-9a-fA-F]{8}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{4}\-[0-9a-fA-F]{12}$")
(def group-id-regex #"^group/([a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?)?$")

(s/def ::user-id
  (assoc (st/spec (s/and string? #(re-matches user-id-regex %)))
    :name "user-id"
    :json-schema/type "string"
    :json-schema/display-name "user ID"
    :json-schema/description "user identifier"))


(s/def ::group-id
  (assoc (st/spec (s/and string? #(re-matches group-id-regex %)))
    :name "group-id"
    :json-schema/type "string"
    :json-schema/display-name "group ID"
    :json-schema/description "group identifier"))


(s/def ::users
  (assoc (st/spec (s/coll-of ::user-id :distinct true))
    :name "users"
    :json-schema/type "array"
    :json-schema/description "list of users in this group"

    :json-schema/order 20))


(s/def ::parents
  (assoc (st/spec (s/coll-of ::group-id :distinct true))
    :name "parents"
    :json-schema/type "array"
    :json-schema/description "list of parents groups"
    :json-schema/server-managed true
    :json-schema/order 30))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::users]
                      :opt-un [::parents]}))
