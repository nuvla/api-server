(ns sixsq.nuvla.db.utils.acl)

;(defn- rule-of-type?
;  [type rule]
;  (= type (:type rule)))
;
;(def user-rule? (partial rule-of-type? "USER"))
;(def role-rule? (partial rule-of-type? "ROLE"))
;(def ^:const acl-users :_acl-users)
;(def ^:const acl-roles :_acl-roles)
;
;
;(defn- rules-with-owner
;  [acl]
;  (let [owner-rule (some-> acl :owner (assoc :right "ALL"))]
;    (->> acl :rules (cons owner-rule) (into #{}) vec)))
;
;
;(defn- extract-by-type
;  [acl type-pred]
;  (->> (rules-with-owner acl) (filter type-pred) (map :principal)))
;
;
;(defn- extract-users
;  [acl]
;  (extract-by-type acl user-rule?))
;
;
;(defn- extract-roles
;  [acl]
;  (extract-by-type acl role-rule?))


;;TODO ACL should be moved somewhere else
(defn force-admin-role-right-all
  [data]
  (update-in data [:acl :edit-acl] #(vec (set (conj % "group/nuvla-admin")))))
