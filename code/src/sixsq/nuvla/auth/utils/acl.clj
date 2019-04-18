(ns sixsq.nuvla.auth.utils.acl
  (:require
    [clojure.set :as set]))


(defn val-as-set
  [[k v]]
  [k (set v)])


(defn remove-owners-from-rights
  [owners acl-sets]
  (let [owners (set owners)
        rights (dissoc acl-sets :owners)]
    (->> rights
         (map (fn [[k v]] [k (set/difference v owners)]))
         (into {})
         (merge {:owners owners}))))


(defn val-as-vector
  [[k v]]
  [k (vec (sort v))])


(defn normalize-acl
  "Takes an ACL and returns the a normalized version of the ACL where all
   rights are listed explicitly and owners do not appear in the lists for
   individual rights."
  [{:keys [owners] :as acl}]
  (->> (dissoc acl :owners)
       (map val-as-set)
       (into {})
       (remove-owners-from-rights owners)
       (remove (fn [[k v]] (empty? v)))
       (map val-as-vector)
       (into {})))


(defn minimal-acl
  "Takes an ACL and returns the minimal version where any rights that can be
   inferred from existing rights are removed."
  [acl]
  acl)
