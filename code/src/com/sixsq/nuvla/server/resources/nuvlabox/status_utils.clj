(ns com.sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.db.es.common.utils :as escu]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.deployment-parameter :as dep-param]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as nb-utils]
    [com.sixsq.nuvla.server.resources.spec.module :as module-spec]
    [com.sixsq.nuvla.server.util.general :as gen-util]
    [com.sixsq.nuvla.server.util.time :as time]))

(def DENORMALIZED_FIELD [:online :inferred-location :nuvlabox-engine-version])

(defn status-fields-to-denormalize
  [nuvlabox-status]
  (->> DENORMALIZED_FIELD
       (select-keys nuvlabox-status)
       (filter (comp some? second))
       (into {})))

(defn denormalize-changes-nuvlabox
  [{:keys [parent] :as nuvlabox-status}]
  (let [propagate-status (status-fields-to-denormalize nuvlabox-status)]
    (when (seq propagate-status)
      (db/scripted-edit parent {:body    {:doc propagate-status}
                                :refresh false}))))

(defn status-telemetry-attributes
  [nuvlabox-status
   {:keys [refresh-interval]
    :or   {refresh-interval nb-utils/default-refresh-interval}
    :as   _nuvlabox}]
  (assoc nuvlabox-status
    :last-telemetry (time/now-str)
    :next-telemetry (nb-utils/compute-next-report refresh-interval #(+ % 30))))

(defn get-ne-deployments
  [{:keys [parent] :as _nuvlabox-status}]
  (let [filter-req (str "nuvlabox='" parent "' and " (u/filter-eq-vals "state" ["STARTED", "UPDATED"]))
        options    {:cimi-params {:filter (parser/parse-cimi-filter filter-req)
                                  :select ["id" "module" "nuvlabox" "acl" "execution-mode"]
                                  :last   10000}}]
    (second (crud/query-as-admin "deployment" options))))

(defn build-nuvlabox-status-acl
  [{:keys [id acl] :as _nuvlabox}]
  (merge
    (select-keys acl [:view-acl :view-data :view-meta])
    {:owners    ["group/nuvla-admin"]
     :edit-data [id]}))

(defn set-name-description-acl
  [nuvlabox-status {:keys [id name] :as nuvlabox}]
  (assoc nuvlabox-status
    :name (nb-utils/format-nb-name
            name (nb-utils/short-nb-id id))
    :description (str "NuvlaEdge status of " (nb-utils/format-nb-name name id))
    :acl (build-nuvlabox-status-acl nuvlabox)))

(defn special-body-nuvlabox
  [{{:keys [parent]} :body :as response} request]
  (if (nb-utils/nuvlabox-request? request)
    (assoc response :body {:jobs (nb-utils/get-jobs parent)})
    response))

(defn detect-swarm
  [{{:keys [parent orchestrator node-id cluster-node-role cluster-managers swarm-node-id] :as _nuvlabox-status} :body :as _response}]
  (let [{:keys [infrastructure-service-group] :as _nuvlabox} (db/retrieve parent)
        swarm-node-id-set? (not (str/blank? swarm-node-id))
        attributes         {:swarm-enabled (or (= "swarm" orchestrator)
                                               swarm-node-id-set?)
                            :swarm-manager (or (= "manager" cluster-node-role)
                                               (contains? (set cluster-managers) node-id)
                                               swarm-node-id-set?)}]

    (log/debugf "detect-swarm - parent: %s - isg: %s - attrs: %s - swarm-node-id: %s - orchestrator: %s - node-id: %s - cluster-node-role: %s - cluster-managers: %s"
                parent infrastructure-service-group attributes swarm-node-id orchestrator node-id cluster-node-role cluster-managers)
    (when-let [resource-id (nb-utils/get-service "swarm" infrastructure-service-group)]
      (let [response (db/scripted-edit resource-id {:refresh false :body {:doc attributes}})]
        (log/debugf "detect-swarm - parent: %s - resource-id: %s - scripted-edit: %s" parent resource-id response)))))

(defn complete-param
  [{:keys [id acl] :as _deployment} deployment-parameter]
  (-> deployment-parameter
      (assoc :parent id
             :created-by "internal"
             :resource-type dep-param/resource-type
             :acl acl)
      (crud/new-identifier dep-param/resource-type)
      u/update-timestamps))

(defn param-bulk-operation-data
  [{:keys [id] :as param}]
  [{:update {:_id (u/id->uuid id) :_index (escu/collection-id->index dep-param/resource-type)}}
   {:doc    (select-keys param [:value :updated])
    :upsert param}])

(defn params-bulk-operation-data
  [params]
  (mapcat param-bulk-operation-data params))

(defn list-global-params
  [{{{:keys [local public swarm vpn]} :ips} :network ip :ip :as _nb-status}]
  [{:name  "ip.local",
    :value local}
   {:name  "ip.public",
    :value public}
   {:name  "ip.swarm",
    :value swarm}
   {:name  "ip.vpn",
    :value vpn}
   {:name  "hostname",
    :value ip}])

(defn param-add-node-id
  [node-id deployment-parameter]
  (assoc deployment-parameter :node-id node-id))

(defn param-prefix-name-by-node-id
  [{:keys [name node-id] :as deployment-parameter}]
  (assoc deployment-parameter :name (str node-id "." name)))

(defn params-for-node-id
  [node-id params]
  (map (comp param-prefix-name-by-node-id (partial param-add-node-id node-id))
       params))

(defn docker-swarm-service-params
  [service]
  (let [node-id (some-> service (get-in [:Spec :Name]) (gen-util/safe-subs 37))]
    (params-for-node-id
      node-id
      (concat
        [{:name  "mode",
          :value (cond
                   (get-in service [:Spec :Mode :Replicated]) "replicated"
                   (get-in service [:Spec :Mode :Global]) "global"
                   :else "")}
         {:name  "replicas.running",
          :value (str (get-in service [:ServiceStatus :RunningTasks]))}
         {:name  "replicas.desired",
          :value (str (get-in service [:ServiceStatus :DesiredTasks]))}
         {:name  "service-id",
          :value (some-> service :ID (gen-util/safe-subs 0 12)),}
         {:name  "node-id",
          :value node-id}
         {:name  "image",
          :value (get-in service [:Spec :Labels :com.docker.stack.image] "")}]
        (keep (fn [{:keys [PublishedPort TargetPort Protocol]}]
                (when PublishedPort
                  {:name  (str Protocol "." TargetPort)
                   :value (str PublishedPort)})) (get-in service [:Endpoint :Ports] []))))))

(defn docker-compose-container-params
  [container]
  (let [node-id (get-in container [:Labels :com.docker.compose.service])]
    (params-for-node-id
      node-id
      (concat [{:name  "image",
                :value (:Image container)}
               {:name  "node-id",
                :value node-id},
               {:name  "service-id",
                :value (:Id container)}]
              (keep (fn [{:keys [PublicPort PrivatePort Type]}]
                      (when PublicPort
                        {:name  (str Type "." PrivatePort)
                         :value (str PublicPort)})) (:Ports container))))))

(defn k8s-deployment-params
  [deployment]
  (let [node-id (str "Deployment." (get-in deployment [:metadata :name] ""))]
    (params-for-node-id
      node-id
      [{:name  "replicas.desired",
        :value (str (get-in deployment [:spec :replicas] ""))}
       {:name  "node-id",
        :value node-id},
       {:name  "replicas.running",
        :value (str (get-in deployment [:status :ready_replicas] 0))}])))

(defn k8s-service-params
  [service]
  (let [node-id (str "Service." (get-in service [:metadata :name] ""))]
    (params-for-node-id
      node-id
      (concat [{:name  "node-id",
                :value node-id}]
              (keep (fn [{:keys [node_port port protocol]}]
                      (when node_port
                        {:name  (str (str/lower-case (or protocol "")) "." port)
                         :value (str node_port)})) (get-in service [:spec :ports]))))))

(defn k8s-helmreleases-params
  [helmrelase]
  [{:name  "helm-name",
    :value (get helmrelase :name "")}
   {:name  "helm-status",
    :value (get helmrelase :status "")}
   {:name  "helm-namespace",
    :value (get helmrelase :namespace "")}
   {:name  "helm-updated",
    :value (get helmrelase :updated "")}
   {:name  "helm-chart",
    :value (get helmrelase :chart "")}
   {:name  "helm-app_version",
    :value (get helmrelase :app_version "")}
   {:name  "helm-revision",
    :value (get helmrelase :revision "")}])

(defmulti get-docker-state (fn [{{:keys [compatibility]} :module :as _deployment} _nb-status] compatibility))

(defmethod get-docker-state module-spec/compatibility-swarm
  [{:keys [id] :as _deployment} nb-status]
  (->> (get-in nb-status [:coe-resources :docker :services])
       (filter #(= (get-in % [:Spec :Labels :com.docker.stack.namespace]) (u/id->uuid id)))
       (mapcat docker-swarm-service-params)))

(defmethod get-docker-state module-spec/compatibility-docker-compose
  [{:keys [id] :as _deployment} nb-status]
  (->> (get-in nb-status [:coe-resources :docker :containers])
       (filter #(= (get-in % [:Labels :com.docker.compose.project]) (u/id->uuid id)))
       (mapcat docker-compose-container-params)))

(defn get-k8s-state
  [{:keys [id] :as _deployment} nb-status]
  (let [uuid            (u/id->uuid id)
        coe-k8s         (get-in nb-status [:coe-resources :kubernetes])
        filter-dep-uuid #(or (= (get-in % [:metadata :labels :nuvla.deployment.uuid]) uuid)
                             (= (get-in % [:metadata :namespace]) uuid))]
    (concat
      (->> (get coe-k8s :deployments)
           (filter filter-dep-uuid)
           (mapcat k8s-deployment-params))
      (->> (get coe-k8s :services)
           (filter filter-dep-uuid)
           (mapcat k8s-service-params))
      (->> (get coe-k8s :helmreleases)
           (filter #(= (get % :namespace) uuid))
           (mapcat k8s-helmreleases-params)))))

(defmulti get-deployment-state (fn [{{:keys [subtype]} :module :as _deployment} _nb-status]
                                 subtype))

(defmethod get-deployment-state module-spec/subtype-app-docker
  [deployment nb-status]
  (get-docker-state deployment nb-status))

(defmethod get-deployment-state module-spec/subtype-app-k8s
  [deployment nb-status]
  (get-k8s-state deployment nb-status))

(defmethod get-deployment-state module-spec/subtype-app-helm
  [deployment nb-status]
  (get-k8s-state deployment nb-status))

(defn get-ne-deployment-params
  [nuvlabox-status ne-deployments]
  (let [global-params (list-global-params nuvlabox-status)]
    (mapcat (fn [deployment]
              (map
                #(complete-param deployment %)
                (concat
                  global-params
                  (get-deployment-state deployment nuvlabox-status))))
            ne-deployments)))

(defn old-docker-detector
  [nuvlabox-status {{:keys [subtype compatibility]} :module id :id :as _deployment}]
  (and (= subtype module-spec/subtype-app-docker)
       (= compatibility module-spec/compatibility-swarm)
       (-> (get-in nuvlabox-status [:coe-resources :docker :services])
           (->> (some #(when (= (get-in % [:Spec :Labels :com.docker.stack.namespace]) (u/id->uuid id)) %)))
           (get-in [:ServiceStatus :DesiredTasks])
           nil?)))

(defn partition-by-old-docker-for-swarm
  [nuvlabox-status ne-deployments]
  (let [result (group-by (partial old-docker-detector nuvlabox-status) ne-deployments)]
    [(get result true [])
     (get result false [])]))

(defn create-deployment-state-job
  [{:keys [id, execution-mode] :as _deployment}
   {:keys [parent] :as _nb-status}]
  (log/debug "Creating deployment_state job for " id)
  (job-utils/create-job id "deployment_state"
                        (-> {:owners [a/group-admin]}
                            (a/acl-append :edit-data parent)
                            (a/acl-append :manage parent))
                        "internal"
                        :execution-mode execution-mode))

(defn update-deployment-parameters
  [nuvlabox-status ne-deployments]
  (let [log-title (str "Update deployment-parameters for " (:parent nuvlabox-status) ":")]
    (try
      (when (:coe-resources nuvlabox-status)
        (let [[old-docker-swarm-deployments
               left-deployments] (partition-by-old-docker-for-swarm nuvlabox-status ne-deployments)
              params (get-ne-deployment-params nuvlabox-status left-deployments)]
          (log/info log-title "Update/inserting" (count params) "parameters")
          (when (seq params)
            (try
              (let [response (db/bulk-operation dep-param/resource-type (params-bulk-operation-data params))
                    summary  (escu/summarise-bulk-operation-response response)]
                (log/debug log-title "summary:" summary))
              (catch Exception e
                (log/error log-title (ex-message e) (ex-data e)))))
          (when (seq old-docker-swarm-deployments)
            (log/info "Creating deployment_state job for old docker "
                      (count old-docker-swarm-deployments) "deployments")
            (create-deployment-state-job old-docker-swarm-deployments nuvlabox-status))))
      (catch Exception e
        (log/error log-title "failed: " e))))
  nuvlabox-status)

(defmulti create-deployment-state-job-if-needed
          (fn [{{:keys [subtype]} :module :as _deployment} _nb-status]
            (if (= subtype module-spec/subtype-app-helm) module-spec/subtype-app-k8s subtype)))

(defmethod create-deployment-state-job-if-needed module-spec/subtype-app-docker
  [deployment {{:keys [docker]} :coe-resources :as nb-status}]
  (when (empty? docker)
    (create-deployment-state-job deployment nb-status)))

(defmethod create-deployment-state-job-if-needed module-spec/subtype-app-k8s
  [deployment {{:keys [kubernetes]} :coe-resources :as nb-status}]
  (when (empty? kubernetes)
    (create-deployment-state-job deployment nb-status)))

(defn create-deployment-state-jobs
  [nuvlabox-status ne-deployments]
  (doseq [deployment ne-deployments]
    (create-deployment-state-job-if-needed deployment nuvlabox-status))
  nuvlabox-status)
