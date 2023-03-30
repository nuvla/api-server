(ns sixsq.nuvla.db.es.acl
  (:require
    [sixsq.nuvla.db.es.query :as ef]))

(defn claims->clauses
  [claims right]
   (let [acl-view-meta-clauses (map vector (repeat (str "acl." right)) claims)
         acl-owners-clauses    (map vector (repeat "acl.owners") claims)]
     (concat acl-view-meta-clauses
             acl-owners-clauses)) )

(defn- clauses->queries
  ([acl-clauses]
   (clauses->queries acl-clauses ef/eq))
  ([acl-clauses ef-fn]
   (map (fn [[field value]] (ef-fn field value)) acl-clauses)))

(defn- acl-queries->query-acl
  ([acl-queries]
   (acl-queries->query-acl acl-queries ef/or))
  ([acl-queries ef-cond-fn]
   (if (empty? acl-queries) (ef/match-none-query) (ef-cond-fn acl-queries))))

(defn and-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options),
   requiring at least the specified right."
  [query {:keys [nuvla/authn] :as _options} right]
  (let [acl-clauses           (claims->clauses (:claims authn) right)
        acl-queries           (clauses->queries acl-clauses)
        query-acl             (acl-queries->query-acl acl-queries)]
    (ef/and [query-acl query])))

(defn and-not-acl
  "Enriches query-builder by adding a clause on ACL (extracted from options),
   requiring the specified right is not present."
  [query {:keys [nuvla/authn] :as _options} right]
  (let [acl-clauses           (claims->clauses (:claims authn) right)
        acl-queries           (clauses->queries acl-clauses ef/ne)
        query-acl             (acl-queries->query-acl acl-queries ef/and)]
    (ef/and [query-acl query])))

(defn and-acl-query
  [query options]
  (and-acl query options "view-meta"))


(defn and-acl-delete
  [query options]
  (and-acl query options "delete"))


(defn and-acl-edit
  [query options]
  (and-acl query options "edit-meta"))

(defn and-not-acl-edit
  [query options]
  (and-not-acl query options "edit-meta"))