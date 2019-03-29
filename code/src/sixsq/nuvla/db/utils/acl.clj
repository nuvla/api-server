(ns sixsq.nuvla.db.utils.acl)

(defn force-admin-role-right-all
  [data]
  (update-in data [:acl :edit-acl] #(vec (set (conj % "group/nuvla-admin")))))
