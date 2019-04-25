(ns sixsq.nuvla.auth.utils.acl
  (:require
    [clojure.set :as set]
    [sixsq.nuvla.auth.acl-resource :as acl-resource]
    [clojure.tools.logging :as log]))


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
  "Takes an ACL and returns a normalized version of the ACL where all
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

(defn kb-remove-owners-from-principals
  [owners-set [right principals]]
  [right (set/difference principals owners-set)])

(defn kb-explode-rights
  [[right principals]]
  (let [qualified-right (get acl-resource/rights-keywords right)
        sub-rights (ancestors acl-resource/rights-hierarchy qualified-right)]
    (conj
      (map (fn [sub-right] {sub-right principals}) sub-rights)
      {qualified-right principals})))

(defn kb-normalize-acl
  "Takes an ACL and returns a normalized version of the ACL where all
   rights are listed explicitly and owners do not appear in the lists for
   individual rights."
  [{:keys [owners] :as acl}]
  (let [owners-set (set owners)
        acl-rights (->> (dissoc acl :owners)
                        (map val-as-set))]
    (->> acl-rights
         (map (partial kb-remove-owners-from-principals owners-set))
         (map kb-explode-rights)
         (reduce concat)
         (reduce (fn [a b] (merge-with set/union a b))))))



(defn minimal-acl
  "Takes an ACL and returns the minimal version where any rights that can be
   inferred from existing rights are removed."
  [acl]
  acl)
