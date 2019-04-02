(ns sixsq.nuvla.server.resources.spec.acl-collection
  "Schema definition for collection Access Control Lists (ACLs)."
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.acl-common :as acl-common]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::query
  (-> (st/spec ::acl-common/principals)
      (assoc :name "query"
             :json-schema/name "query"
             :json-schema/type "Array"
             :json-schema/display-name "query"
             :json-schema/description "list of principals that can query a collection")))


(s/def ::add
  (-> (st/spec ::acl-common/principals)
      (assoc :name "add"
             :json-schema/name "add"
             :json-schema/type "Array"
             :json-schema/display-name "add"
             :json-schema/description "list of principals that can add a new resource to a collection")))


(s/def ::acl
  (-> (st/spec (su/only-keys :opt-un [::query ::add]))
      (assoc :name "acl"
             :json-schema/name "acl"
             :json-schema/type "map"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory false
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "ACL"
             :json-schema/description "collection ACL"
             :json-schema/help "collection Access Control List (ACL)"
             :json-schema/group "acl"
             :json-schema/category "Access Control List"
             :json-schema/order 0
             :json-schema/hidden false
             :json-schema/sensitive false)))
