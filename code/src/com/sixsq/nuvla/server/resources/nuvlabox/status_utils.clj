(ns com.sixsq.nuvla.server.resources.nuvlabox.status-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as acl-resource]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.deployment-parameter :as dep-param]
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

(defn ne-deployments
  [{:keys [parent] :as _nuvlabox-status}]
  (let [filter-req (str "nuvlabox='" parent "' and " (u/filter-eq-vals "state" ["STARTED", "UPDATED"]))
        options    {:cimi-params {:filter (parser/parse-cimi-filter filter-req)
                                  :select ["id" "module" "nuvlabox" "state"]
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
  [deployment-id deployment-parameter]
  (-> deployment-parameter
      (assoc :parent deployment-id
             :created-by "internal"
             :resource-type dep-param/resource-type
             :acl {:owners   [acl-resource/group-admin]
                   :edit-acl [deployment-id]})
      (crud/new-identifier dep-param/resource-type)
      u/update-timestamps))

(defn param-bulk-operation-data
  [{:keys [id] :as param}]
  [{:update {:_id id :_index dep-param/resource-type}}
   {:doc    (select-keys param [:value])
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
      [{:name  "mode",
        :value (cond
                 ;; todo support all modes
                 (get-in service [:Spec :Mode :Replicated]) "replicated"
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
        :value (get-in service [:Spec :Labels :com.docker.stack.image] "")}])))

(defn docker-compose-container-params
  [container]
  (let [node-id (get-in container [:Labels :com.docker.compose.service])]
    (params-for-node-id
      node-id
      [{:name  "image",
        :value (:Image container)}
       {:name  "node-id",
        :value node-id},
       {:name  "service-id",
        :value (:Id container)}])))

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

(defmulti get-deployment-state (fn [{{:keys [subtype]} :module :as _deployment} _nb-status]
                                 subtype))

(defmethod get-deployment-state module-spec/subtype-app-docker
  [deployment nb-status]
  (get-docker-state deployment nb-status))

(defn get-ne-deployment-params
  [nuvlabox-status nb-deployments]
  (let [global-params  (list-global-params nuvlabox-status)]
    (mapcat (fn [{:keys [id] :as deployment}]
              (map
                #(complete-param id %)
                (concat
                  global-params
                  (get-deployment-state deployment nuvlabox-status))))
            nb-deployments)))

(defn query-ne-deployments-get-params
  [nuvlabox-status]
  (get-ne-deployment-params
    nuvlabox-status
    (ne-deployments nuvlabox-status)))

(defn update-deployment-parameters
  [nuvlabox-status nuvlabox]
  (when (nb-utils/is-nb-v2-17-or-newer? nuvlabox)
    (log/warn "update-deployment-parameters is-nb-v2-17-or-newer?:" true)
    (let [params (query-ne-deployments-get-params nuvlabox-status)]
      (log/warn "update-deployment-parameters params:" params)
      (when (seq params)
        (try
          (let [response (db/bulk-operation dep-param/resource-type (params-bulk-operation-data params))]
            (log/info "Update-deployment-parameters:" params "\nResponse:" response))
          (catch Exception e
            (log/warn "Update-deployment-parameters with errors:" params "\nResponse:" (ex-message e)))))))
  nuvlabox-status)
