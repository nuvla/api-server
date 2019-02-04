(ns com.sixsq.slipstream.db.es-rest.acl
  (:require
    [com.sixsq.slipstream.db.es-rest.query :as ef]
    [com.sixsq.slipstream.db.utils.acl :as acl-utils]))

(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options)"
  [query {:keys [user-name user-roles] :as options}]
  (let [user-name-clause (when user-name [[acl-utils/acl-users user-name]])
        user-roles-clauses (map vector (repeat acl-utils/acl-roles) user-roles)
        acl-clauses (concat user-name-clause user-roles-clauses)
        acl-queries (map (fn [[field value]] (ef/eq field value)) acl-clauses)
        query-acl (if (empty? acl-queries) (ef/match-none-query) (ef/or acl-queries))]
    (ef/and [query-acl query])))
