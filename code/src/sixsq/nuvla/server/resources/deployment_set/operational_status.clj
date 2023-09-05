(ns sixsq.nuvla.server.resources.deployment-set.operational-status)


(defn deployments-to-add
  [expected current]
  (->> expected
       (filter #(not (contains? current (first %))))
       vals
       set))


(defn deployments-to-remove
  [expected current]
  (->> current
       (filter #(not (contains? expected (first %))))
       (map (comp :id second))
       set))


(defn update-app?
  [{{v1 :version env1 :environmental-variables} :application :as _deployment1}
   {{v2 :version env2 :environmental-variables} :application :as _deployment2}]
  (or (not= v1 v2) (not= env1 env2)))


(defn deployments-to-update
  [expected current]
  (->> expected
       (keep (fn [[k d]]
               (when-let [current-deployment (get current k)]
                 (when (update-app? current-deployment d)
                   [(:id current-deployment) current-deployment]))))
       (into {})))


(defn index-by
  "Like group-by but expects exactly one value per grouped key.
   If more than one value is present, the first one returned by `group-by` will be taken."
  [f coll]
  (->> coll
       (group-by f)
       (map (fn [[k v]] [k (first v)]))
       (into {})))


(defn remove-empty-entries
  "Remove map entries with empty value."
  [m]
  (->> m
       (filter (fn [[_ v]] (seq v)))
       (into {})))


(def deployment-unique-key-fn
  (juxt :app-set (comp :id :application) :target))


(defn divergence-map
  "Given a set of expected deployments and a set of current deployments, computes a `divergence map` describing
   the actions to be taken to move from the current deployments situation to the expected one.

   Expected deployments must have the following shape:
   ```
   {:app-set \"set-1\",
    :application {:id \"module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8\",
                  :version 1,
                  :environmental-variables [{:name \"var_1\", :value \"val1\"}
                                            {:name \"var_2\", :value \"val2\"}]},
    :target \"credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316\"
   }
   ```
   Current deployments must have the same form of expected deployments, with an additional `id` property.

   A map with the following keys is returned:
   - `:deployments-to-add` is the set of deployments to be added;
   - `:deployments-to-remove` is the set of deployment ids to be removed;
   - `:deployments-to-update` is a map from deployment id to updated definition of the deployments to update.\n
   For example:
   ```
   {:deployments-to-add #{{:app-set     \"set-1\"
                           :application {:environmental-variables [{:name  \"var_1\" :value \"val1\"}
                                                                   {:name  \"var_2\" :value \"val2\"}]
                                         :id                      \"module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8\"
                                         :version                 1}
                           :target      \"credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316\"}}
    :deployments-to-remove #{\"id1\" \"id2\" \"id3\"}
    :deployments-to-update {\"id4\" {:app-set     \"set-1\"
                                     :application {:environmental-variables [{:name  \"var_1\" :value \"val1\"}
                                                                             {:name  \"var_2\" :value \"val2\"}]
                                                   :id                      \"module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8\"
                                                   :version                 1}
                                     :target      \"credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316\"}}
                             \"id5\" ...}
   }
   ```
  "
  [expected current]
  (let [expected1 (index-by deployment-unique-key-fn expected)
        current1  (index-by deployment-unique-key-fn current)]
    (remove-empty-entries
      {:deployments-to-add    (deployments-to-add expected1 current1)
       :deployments-to-remove (deployments-to-remove expected1 current1)
       :deployments-to-update (deployments-to-update expected1 current1)})))
