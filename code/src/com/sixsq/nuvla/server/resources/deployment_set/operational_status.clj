(ns com.sixsq.nuvla.server.resources.deployment-set.operational-status)

(def deployments-to-add-k :deployments-to-add)
(def deployments-to-update-k :deployments-to-update)
(def deployments-to-remove-k :deployments-to-remove)

(defn operational-status-values-set
  [deployment-set]
  (update deployment-set :operational-status
          (fn [operational-status]
            (->> operational-status
                 (map (fn [[k v]]
                        (if (#{deployments-to-add-k deployments-to-update-k deployments-to-remove-k} k)
                          [k (set v)]
                          [k v])))
                 (into {})))))

(defn deployments-to-add
  [expected current]
  (set (keep (fn [[k [d]]] (when (not (contains? current k)) d)) expected)))

(defn deployments-to-remove
  [expected current]
  (set
    (mapcat
      (fn [[k deployments]]
        (map :id (cond
                   (not (contains? expected k)) deployments ;; not expected remove all
                   (> (count deployments) 1) (rest deployments) ;; duplicated keep only 1 deployment
                   :else [])))
      current)))

(defn update-app?
  [{{v1 :version env1 :environmental-variables files1 :files} :application :as _expected-deployment}
   {{v2 :version env2 :environmental-variables files2 :files} :application :keys [state] :as _current-deployment}]
  (let [relevant-env-keys   (set (map :name env1))
        env2                (filter #(relevant-env-keys (:name %)) env2)
        relevant-file-names (set (map :file-name files1))
        files2              (filter #(relevant-file-names (:file-name %)) files2)]
    (or (not= v1 v2)
        (not= (seq env1) (seq env2))
        (not= (seq files1) (seq files2))
        (and (not= "STARTED" state)
             (not= "UPDATED" state)))))

(defn deployments-to-update
  [expected current]
  (->> expected
       (keep (fn [[k [d]]]
               (when-let [current-deployment (first (get current k))]
                 (when (update-app? d current-deployment)
                   [current-deployment d]))))
       set))


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
                  :files [{:file-name \"file1\", :file-content \"file1 content\"}
                          {:file-name \"file2\", :file-content \"file2 content\"}]
    :target \"credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316\"
   }
   ```
   Current deployments must have the same form of expected deployments, with an additional `id` property.

   A map with the following keys is returned:
   - `:deployments-to-add` is the set of deployments to be added;
   - `:deployments-to-remove` is the set of deployment ids to be removed;
   - `:deployments-to-update` is a set of pairs of current deployment and desired deployment for deployments that
      already exist but need to be updated.\n
   For example:
   ```
   {:deployments-to-add #{{:app-set     \"set-1\"
                           :application {:environmental-variables [{:name  \"var_1\" :value \"val1\"}
                                                                   {:name  \"var_2\" :value \"val2\"}]
                                         :files [{:file-name \"file1\", :file-content \"file1 content\"}
                                                 {:file-name \"file2\", :file-content \"file2 content\"}]
                                         :id                      \"module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8\"
                                         :version                 1}
                           :target      \"credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316\"}}
    :deployments-to-remove #{\"id1\" \"id2\" \"id3\"}
    :deployments-to-update #{[{:id          \"12345\"
                               :state       \"STARTING\"
                               :app-set     \"set-1\"
                               :application {:environmental-variables [{:name  \"var_1\" :value \"val1\"}
                                                                       {:name  \"var_2\" :value \"val2\"}]
                                             :files [{:file-name \"file1\", :file-content \"file1 content\"}
                                                     {:file-name \"file2\", :file-content \"file2 content\"}]
                                             :id                      \"module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8\"
                                             :version                 1}
                               :target      \"credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316\"}}
                              {:app-set     \"set-1\"
                               :application {:environmental-variables [{:name  \"var_1\" :value \"val1\"}
                                                                       {:name  \"var_2\" :value \"val2\"}]
                                             :files [{:file-name \"file1\", :file-content \"file1 content\"}
                                                     {:file-name \"file2\", :file-content \"file2 content\"}]
                                             :id                      \"module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8\"
                                             :version                 1}
                              :target      \"credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316\"}}
                             ....
                             [...]}
   }
   ```
  "
  [expected current]
  (let [expected1 (group-by deployment-unique-key-fn expected)
        current1  (group-by deployment-unique-key-fn current)]
    (remove-empty-entries
      {deployments-to-add-k    (deployments-to-add expected1 current1)
       deployments-to-remove-k (deployments-to-remove expected1 current1)
       deployments-to-update-k (deployments-to-update expected1 current1)})))
