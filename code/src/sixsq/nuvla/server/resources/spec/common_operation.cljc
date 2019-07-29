(ns sixsq.nuvla.server.resources.spec.common-operation
  "Spec definitions for common operation types used in CIMI resources."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::href
  (-> (st/spec ::core/uri)
      (assoc :name "href"
             :json-schema/description "URI for operation"

             :json-schema/server-managed true
             :json-schema/editable false)))


(s/def ::rel
  (-> (st/spec ::core/url)
      (assoc :name "rel"
             :json-schema/description "URL for performing action"

             :json-schema/server-managed true
             :json-schema/editable false)))


(s/def ::operation
  (-> (st/spec (su/only-keys :req-un [::href ::rel]))
      (assoc :name "operation"
             :json-schema/type "map"
             :json-schema/description "operation definition (name, URL) for a resource"

             :json-schema/server-managed true
             :json-schema/editable false)))


(s/def ::operations
  (-> (st/spec (s/coll-of ::operation :min-count 1))
      (assoc :name "operations"
             :json-schema/type "array"
             :json-schema/description "list of authorized resource operations"
             :json-schema/section "meta"

             :json-schema/server-managed true
             :json-schema/editable false
             :json-schema/order 0
             :json-schema/indexed false)))
