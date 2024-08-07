(ns com.sixsq.nuvla.server.resources.spec.acl-resource
  "Schema definition for resource Access Control Lists (ACLs)."
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.acl-common :as acl-common]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::owners
  (-> (st/spec (s/coll-of ::acl-common/principal :kind vector? :distinct true :min-count 1))
      (assoc :name "owners"
             :json-schema/description "list of owners of a resource")))


(s/def ::view-meta
  (-> (st/spec ::acl-common/principals)
      (assoc :name "view-meta"
             :json-schema/display-name "view metadata"
             :json-schema/description "list of principals that can view resource metadata")))


(s/def ::view-data
  (-> (st/spec ::acl-common/principals)
      (assoc :name "view-data"
             :json-schema/display-name "view data"
             :json-schema/description "list of principals that can view resource data")))


(s/def ::view-acl
  (-> (st/spec ::acl-common/principals)
      (assoc :name "view-acl"
             :json-schema/display-name "view ACL"
             :json-schema/description "list of principals that can view resource ACL")))


(s/def ::edit-meta
  (-> (st/spec ::acl-common/principals)
      (assoc :name "edit-meta"
             :json-schema/display-name "edit metadata"
             :json-schema/description "list of principals that can edit resource metadata")))


(s/def ::edit-data
  (-> (st/spec ::acl-common/principals)
      (assoc :name "edit-data"
             :json-schema/display-name "edit data"
             :json-schema/description "list of principals that can edit resource data")))


(s/def ::edit-acl
  (-> (st/spec ::acl-common/principals)
      (assoc :name "edit-acl"
             :json-schema/display-name "edit ACL"
             :json-schema/description "list of principals that can edit resource ACL")))


(s/def ::manage
  (-> (st/spec ::acl-common/principals)
      (assoc :name "manage"
             :json-schema/description "list of principals that can manage a resource via custom actions")))


(s/def ::delete
  (-> (st/spec ::acl-common/principals)
      (assoc :name "delete"
             :json-schema/description "list of principals that can delete a resource")))


(s/def ::acl
  (-> (st/spec (su/only-keys :req-un [::owners]
                             :opt-un [::view-meta ::view-data ::view-acl
                                      ::edit-meta ::edit-data ::edit-acl
                                      ::manage
                                      ::delete]))
      (assoc :name "acl"
             :json-schema/type "map"
             :json-schema/display-name "ACL"
             :json-schema/description "resource ACL"
             :json-schema/section "acl"

             :json-schema/order 0)))
