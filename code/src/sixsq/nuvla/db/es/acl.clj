(ns sixsq.nuvla.db.es.acl
  (:require
    [sixsq.nuvla.db.es.query :as ef]))


(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options)"
  [query {:keys [nuvla/authn] :as _options} right]
  (let [claims                (:claims authn)
        acl-view-meta-clauses (map vector (repeat (str "acl." right)) claims)

        acl-owners-clauses    (map vector (repeat "acl.owners") claims)
        acl-clauses           (concat acl-view-meta-clauses
                                      acl-owners-clauses)
        acl-queries           (map (fn [[field value]] (ef/term-query field value)) acl-clauses)
        query-acl             (if (empty? acl-queries) (ef/match-none-query) (ef/or-clauses acl-queries))]
    (ef/and-clauses [query-acl query])))


(defn and-acl-query
  [query options]
  (and-acl query options "view-meta"))


(defn and-acl-delete
  [query options]
  (and-acl query options "delete"))


(defn and-acl-edit
  [query options]
  (and-acl query options "edit-data"))
