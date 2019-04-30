(ns sixsq.nuvla.db.es.acl
  (:require
    [sixsq.nuvla.db.es.query :as ef]))


(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options)"
  [query {:keys [nuvla/authn] :as options}]
  (let [claims (:claims authn)
        acl-view-meta-clauses (map vector (repeat "acl.view-meta") claims)

        acl-owners-clauses (map vector (repeat "acl.owners") claims)
        acl-clauses (concat acl-view-meta-clauses
                            acl-owners-clauses)
        acl-queries (map (fn [[field value]] (ef/eq field value)) acl-clauses)
        query-acl (if (empty? acl-queries) (ef/match-none-query) (ef/or acl-queries))]
    (ef/and [query-acl query])))
