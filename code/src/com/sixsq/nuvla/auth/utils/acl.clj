(ns com.sixsq.nuvla.auth.utils.acl
  (:require
    [clojure.set :as set]
    [com.sixsq.nuvla.auth.acl-resource :as acl-resource]))


(defn val-as-set
  [[k v]]
  [k (set v)])

(defn val-as-vector
  [[k v]]
  [k (vec (sort v))])


(defn remove-owners-from-rights
  [owners-set [right principals]]
  [right (set/difference principals owners-set)])


(defn merge-rights
  ([] {})
  ([acl-a] acl-a)
  ([acl-a acl-b] (merge-with set/union acl-a acl-b)))


(defn extend-rights
  [[right principals]]
  (let [qualified-right (get acl-resource/rights-keywords right)
        sub-rights      (ancestors acl-resource/rights-hierarchy qualified-right)]
    (conj
      (map (fn [sub-right] {(-> sub-right (name) (keyword)) principals}) sub-rights)
      {right principals})))


(defn normalize-acl
  "Takes an ACL and returns a normalized version of the ACL where all
   rights are listed explicitly and owners do not appear in the lists for
   individual rights."
  [{:keys [owners] :as acl}]
  (let [owners-set        (set owners)
        normalized-rights (->> (dissoc acl :owners)
                               (map val-as-set)
                               (map (partial remove-owners-from-rights owners-set))
                               (remove (fn [[_ principals]] (empty? principals)))
                               (mapcat extend-rights)
                               (reduce merge-rights))]
    (->> (assoc normalized-rights :owners owners-set)
         (map val-as-vector)
         (into {}))))


(defn normalize-acl-for-resource
  [{:keys [acl] :as resource}]
  (assoc resource :acl (normalize-acl acl)))


(defn force-admin-role-right-all
  [resource]
  (update-in resource [:acl :edit-acl] #(vec (set (conj % "group/nuvla-admin")))))
