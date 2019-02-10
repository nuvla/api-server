(ns sixsq.nuvla.server.resources.deployment.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.module :as m]))


(def node-output-params [{:parameter   "abort"
                          :description "Machine abort flag, set when aborting"},
                         {:parameter   "url.ssh"
                          :description "SSH URL to connect to virtual machine"},
                         {:parameter   "password.ssh"
                          :description "SSH password if available"},
                         {:parameter   "keypair.name"
                          :description "SSH keypair name used"},
                         {:parameter   "url.service"
                          :description "Optional service URL for virtual machine"},
                         {:parameter   "statecustom"
                          :description "Custom state"},
                         {:parameter   "complete"
                          :description "'true' when current state is completed"},
                         {:parameter   "instanceid"
                          :description "Cloud instance id"},
                         {:parameter   "hostname"
                          :description "Hostname or IP address of the image"}])

(def node-input-params [{:parameter   "credential.id"
                         :description "Cloud credential ID for managing node deployment"}])

(def global-parameters [{:parameter   "ss:complete"
                         :description "Global complete flag, set when run completed"},
                        {:parameter   "ss:state"
                         :description "Global execution state"
                         :value       "Provisioning"},
                        {:parameter   "ss:abort"
                         :description "Run abort flag, set when aborting"},
                        {:parameter   "ss:url.service"
                         :description "Optional service URL for the deployment"}])


(defn add-global-params
  [deployment-template]
  (assoc deployment-template :outputParameters global-parameters))


(defn add-service-params
  [module-comp-or-img]
  (-> module-comp-or-img
      (update-in [:content :outputParameters] concat node-output-params)
      (update-in [:content :inputParameters] concat node-input-params)))


(defn content-merge
  [{:keys [content] :as module} content-aggregator]
  (-> content
      (dissoc :parentModule)
      (merge content-aggregator)))


(defn params-reducer-fn
  [type]
  (fn [params-aggregator parameter]
    (let [param-name-key (-> parameter :parameter keyword)
          typed-parameter (assoc parameter :type type)]
      (if (contains? params-aggregator param-name-key)
        (update params-aggregator param-name-key
                merge typed-parameter (param-name-key params-aggregator))
        (assoc params-aggregator param-name-key typed-parameter)))))


(defn params-merge
  [{:keys [content] :as module} params-aggregator]
  (let [input-params (get content :inputParameters [])
        output-params (get content :outputParameters [])
        reduce-fn (fn [params-aggregator type params]
                    (reduce (params-reducer-fn type) params-aggregator params))]
    (-> params-aggregator
        (reduce-fn "input" input-params)
        (reduce-fn "output" output-params))))


(defn params->original-format
  [type params]
  (sequence
    (comp
      (filter #(= type (:type %)))
      (map #(dissoc % :type)))
    (vals params)))


(defn targets-merge
  [{:keys [content] :as module} targets-aggregator]
  (let [targets (get content :targets {})]
    (reduce (fn [targets-aggregator [k v]]
              (let [f (if (string? v) cons concat)]
                (assoc targets-aggregator k (f v (k targets-aggregator)))))
            targets-aggregator targets)))


(defn image-ids-merge
  [{:keys [content] :as module} image-ids-aggregator]
  (merge (get content :imageIDs {}) image-ids-aggregator))


(defn combine-modules-comp-img
  [module-comp-or-img]
  (let [module-meta (dissoc module-comp-or-img :content)]
    (loop [module module-comp-or-img
           content-aggregator {}
           params-aggregator {}
           targets-aggregator {}
           image-ids-aggregator {}]
      (if-not module
        (assoc module-meta :content (-> content-aggregator
                                        (assoc :inputParameters (params->original-format "input" params-aggregator))
                                        (assoc :outputParameters (params->original-format "output" params-aggregator))
                                        (assoc :targets targets-aggregator)
                                        (assoc :imageIDs image-ids-aggregator)))
        (recur (-> module :content :parentModule)
               (content-merge module content-aggregator)
               (params-merge module params-aggregator)
               (targets-merge module targets-aggregator)
               (image-ids-merge module image-ids-aggregator))))))


(defn resolve-deployment-template
  [{{:keys [type]} :module :as resolved-body}]
  (if (#{"COMPONENT" "IMAGE"} type)
    (let [combined-modules (-> resolved-body
                               :module
                               add-service-params
                               combine-modules-comp-img)]
      (assoc resolved-body :module combined-modules))
    (let [nodes (get-in resolved-body [:module :content :nodes])
          nodes-combined-modules (map (fn [{:keys [component] :as node}]
                                        (let [combined-module (-> component
                                                                  add-service-params
                                                                  combine-modules-comp-img)]
                                          (assoc node :component combined-module))) nodes)]
      (assoc-in resolved-body [:module :content :nodes] nodes-combined-modules))))


(defn resolve-module [module-href idmap]
  (let [request-module {:params   {:uuid          (some-> module-href (str/split #"/") second)
                                   :resource-name m/resource-type}
                        :identity idmap}
        {:keys [body status] :as module-response} (crud/retrieve request-module)
        parent-href (get-in body [:content :parentModule :href])]
    (if (= status 200)
      (let [module-resolved (-> body
                                (dissoc :versions :operations)
                                (update :content #(dissoc % :parentModule))
                                (std-crud/resolve-hrefs idmap true))]
        (if parent-href
          (assoc-in module-resolved [:content :parentModule] (resolve-module parent-href idmap))
          (assoc module-resolved :href module-href)))
      (throw (ex-info nil body)))))


(defn resolve-hrefs
  [deployment-template idmap]
  (let [module-href (get-in deployment-template [:module :href])]
    (let [module (-> (resolve-module module-href idmap)
                     (assoc :href module-href))]
      (assoc deployment-template :module module))))


(defn create-deployment-template
  [body idmap]
  (let [resolved-body (-> body
                          (resolve-hrefs idmap)
                          (add-global-params))]
    (resolve-deployment-template resolved-body)))

