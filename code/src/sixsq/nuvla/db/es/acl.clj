(ns sixsq.nuvla.db.es.acl
  (:require
    [sixsq.nuvla.db.es.query :as ef]))

;;TODO ACL temporary fix and-acl _acl-users _acl-roles to be deleted
(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options)"
  [query {:keys [user-name user-roles] :as options}]
  (let [roles-with-user (remove nil? (conj user-roles user-name))
        acl-view-clauses (map vector (repeat "acl.view-acl") roles-with-user)
        acl-edit-clauses (map vector (repeat "acl.edit-acl") roles-with-user)
        acl-owners-clauses (map vector (repeat "acl.owners") roles-with-user)
        acl-clauses (concat acl-view-clauses acl-edit-clauses acl-owners-clauses)
        acl-queries (map (fn [[field value]] (ef/eq field value)) acl-clauses)
        query-acl (if (empty? acl-queries) (ef/match-none-query) (ef/or acl-queries))]
    (ef/and [query-acl query])))
