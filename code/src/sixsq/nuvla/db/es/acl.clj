(ns sixsq.nuvla.db.es.acl
  (:require
    [sixsq.nuvla.db.es.query :as ef]))


;; FIXME: Normalize ACLs so that the query becomes much less complex.
(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options)"
  [query {:keys [nuvla/authn] :as options}]
  (let [claims (:claims authn)
        acl-view-meta-clauses (map vector (repeat "acl.view-meta") claims)
        acl-view-data-clauses (map vector (repeat "acl.view-data") claims)
        acl-view-acl-clauses (map vector (repeat "acl.view-acl") claims)

        acl-edit-meta-clauses (map vector (repeat "acl.edit-meta") claims)
        acl-edit-data-clauses (map vector (repeat "acl.edit-data") claims)
        acl-edit-acl-clauses (map vector (repeat "acl.edit-acl") claims)

        acl-owners-clauses (map vector (repeat "acl.owners") claims)
        acl-clauses (concat acl-view-meta-clauses acl-view-data-clauses acl-view-acl-clauses
                            acl-edit-meta-clauses acl-edit-data-clauses acl-edit-acl-clauses
                            acl-owners-clauses)
        acl-queries (map (fn [[field value]] (ef/eq field value)) acl-clauses)
        query-acl (if (empty? acl-queries) (ef/match-none-query) (ef/or acl-queries))]
    (ef/and [query-acl query])))
