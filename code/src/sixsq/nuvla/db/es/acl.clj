(ns sixsq.nuvla.db.es.acl
  (:require
    [sixsq.nuvla.db.es.query :as ef]
    [sixsq.nuvla.db.utils.acl :as acl-utils]))

(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options)"
  [query {:keys [user-name user-roles] :as options}]
  (let [user-name-clause (when user-name [[(name acl-utils/acl-users) user-name]])
        user-roles-clauses (map vector (repeat (name acl-utils/acl-roles)) user-roles)
        acl-clauses (concat user-name-clause user-roles-clauses)
        acl-queries (map (fn [[field value]] (ef/eq field value)) acl-clauses)
        query-acl (if (empty? acl-queries) (ef/match-none-query) (ef/or acl-queries))]
    (ef/and [query-acl query])))
