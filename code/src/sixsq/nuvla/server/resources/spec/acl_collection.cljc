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
             :json-schema/description "list of principals that can query a collection")))


(s/def ::add
  (-> (st/spec ::acl-common/principals)
      (assoc :name "add"
             :json-schema/description "list of principals that can add a new resource to a collection")))


(s/def ::bulk-delete
  (-> (st/spec ::acl-common/principals)
      (assoc :name "bulk-delete"
             :json-schema/description "list of principals that can bulk delete to a collection")))


(s/def ::acl
  (-> (st/spec (su/only-keys :opt-un [::query ::add ::bulk-delete]))
      (assoc :name "acl"
             :json-schema/type "map"
             :json-schema/display-name "ACL"
             :json-schema/description "collection ACL"

             :json-schema/section "acl"
             :json-schema/order 0)))
