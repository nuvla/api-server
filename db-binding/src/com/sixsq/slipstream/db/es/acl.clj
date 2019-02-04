(ns com.sixsq.slipstream.db.es.acl
  (:require
    [com.sixsq.slipstream.db.es.filter :as ef]
    [com.sixsq.slipstream.db.utils.acl :as acl-utils]))

(def ^:private query-no-result (ef/term-query "id" ""))

(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options)"
  [query-builder {:keys [user-name user-roles] :as options}]
  (let [user-name-clause (when user-name [[(name acl-utils/acl-users) user-name]])
        user-roles-clauses (map vector (repeat (name acl-utils/acl-roles)) user-roles)
        acl-clauses (concat user-name-clause user-roles-clauses)
        acl-queries (map (fn [[field value]] (ef/term-query field value)) acl-clauses)
        query-acl (if (empty? acl-queries) query-no-result (ef/or-query acl-queries))]
    (ef/and-query [query-acl query-builder])))
