(ns com.sixsq.nuvla.server.resources.mec.nuvla-backed-mepm
  "Helpers for southbound MEPM implementations that materialize real backing
   Nuvla deployments for MEC app instances."
  (:require
    [clj-http.client :as http]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.mec.app-instance :as app-instance]
    [environ.core :as env]
    [jsonista.core :as json]))


(def ^:private backend-mode
  "NUVLA_BACKED")

(def ^:private default-nuvla-endpoint
  "http://localhost:8200")

(def ^:private native-module-project-path
  "mec-native")

(def ^:private native-module-project-name
  "MEC Native")

(def ^:private native-module-project-description
  "Auto-generated native backing applications for ETSI MEC modules.")

(def ^:private mec-tag
  "MEC")

(def ^:private swarm-credential-subtype
  "infrastructure-service-swarm")

(defonce ^:private nuvla-auth-overrides
  (atom {}))

(def ^:private max-debug-log-size 200)

(defonce ^:private reverse-debug-log
  (atom []))


(defn- request-value
  [payload & paths]
  (some #(get-in payload %) paths))


(defn set-nuvla-auth!
  [{:keys [endpoint api-key api-secret]}]
  (swap! nuvla-auth-overrides merge
         (cond-> {}
           endpoint (assoc :endpoint endpoint)
           api-key (assoc :api-key api-key)
           api-secret (assoc :api-secret api-secret))))


(defn clear-nuvla-auth!
  []
  (reset! nuvla-auth-overrides {}))


(defn clear-debug-log!
  []
  (reset! reverse-debug-log []))


(defn get-debug-log
  []
  @reverse-debug-log)


(defn- append-debug-log!
  [entry]
  (swap! reverse-debug-log
         (fn [entries]
           (->> (conj entries entry)
                (take-last max-debug-log-size)
                vec))))


(defn- trim-trailing-slashes
  [value]
  (some-> value (str/replace #"/+$" "")))


(defn- parse-response-body
  [body]
  (when (and (string? body) (not (str/blank? body)))
    (try
      (json/read-value body json/keyword-keys-object-mapper)
      (catch Exception _
        body))))


(defn- http-response
  [response]
  (update response :body parse-response-body))


(defn- safe-response
  [response]
  (select-keys response [:status :body :headers]))


(defn- header-value
  [headers header-name]
  (some (fn [[k v]]
          (when (= (str/lower-case (name k)) (str/lower-case header-name))
            v))
        headers))


(defn- session-cookie-header
  [response]
  (let [cookie-kv  (some-> response :cookies seq first)
        cookie-str (if cookie-kv
                     (let [[cookie-name {:keys [value]}] cookie-kv]
                       (when value
                         (str (name cookie-name) "=" value)))
                     (let [set-cookie (header-value (:headers response) "set-cookie")]
                       (cond
                         (string? set-cookie) set-cookie
                         (sequential? set-cookie) (first set-cookie)
                         :else nil)))]
    (some-> cookie-str
            (str/split #";" 2)
            first
            str/trim
            not-empty)))


(defn- cookie-log-summary
  [cookie-header]
  (when cookie-header
    (let [[cookie-name cookie-value] (str/split cookie-header #"=" 2)
          prefix-len                 (min 16 (count (or cookie-value "")))]
      {:cookie-name          cookie-name
       :cookie-value-prefix  (subs (or cookie-value "") 0 prefix-len)
       :cookie-value-length  (count (or cookie-value ""))})))


(defn- nuvla-auth-config
  []
  (let [{:keys [endpoint api-key api-secret]} @nuvla-auth-overrides
        endpoint   (trim-trailing-slashes (or endpoint
                                             (env/env :nuvla-endpoint default-nuvla-endpoint)))
        api-key    (or api-key (env/env :nuvla-api-key))
        api-secret (or api-secret (env/env :nuvla-api-secret))]
    (when (str/blank? api-key)
      (throw (ex-info "Missing NUVLA_API_KEY for NUVLA_BACKED MEPM"
                      {:status 500})))
    (when (str/blank? api-secret)
      (throw (ex-info "Missing NUVLA_API_SECRET for NUVLA_BACKED MEPM"
                      {:status 500})))
    {:endpoint   endpoint
     :api-key    api-key
     :api-secret api-secret}))


(defn- nuvla-api-url
  [{:keys [endpoint]} path]
  (str endpoint "/api/" path))


(defn- error-message
  [response fallback]
  (or (get-in response [:body :detail])
      (get-in response [:body :message])
      (get-in response [:body :error])
      fallback))


(defn- request!
  [client method path & [{:keys [body ok-statuses]
                          :or   {ok-statuses #{200}}
                          :as   options}]]
  (let [headers  (cond-> {"accept" "application/json"}
                   (:cookie-header client)
                   (assoc "cookie" (:cookie-header client)))
        request-summary {:method         (-> method name str/upper-case)
                         :path           path
                         :query-params   (:query-params options)
                         :has-cookie?    (contains? headers "cookie")
                         :cookie-summary (cookie-log-summary (get headers "cookie"))}
        _        (log/info "NUVLA_BACKED reverse request" request-summary)
        response (-> (http/request (cond-> {:method           method
                                            :url              (nuvla-api-url client path)
                                            :throw-exceptions false
                                            :headers          headers
                                            :as               :text}
                                     (:query-params options)
                                     (assoc :query-params (:query-params options))
                                     body (assoc :body (json/write-value-as-string body)
                                                 :content-type :json)))
                     http-response)]
    (append-debug-log! (assoc request-summary
                              :status (:status response)
                              :response-body (:body response)
                              :response-cookie-summary (cookie-log-summary (session-cookie-header response))))
    (log/info "NUVLA_BACKED reverse response"
              {:method (-> method name str/upper-case)
               :path   path
               :status (:status response)})
    (if (contains? ok-statuses (:status response))
      response
      (throw (ex-info (error-message response
                                     (format "Nuvla API %s %s failed"
                                             (-> method name str/upper-case)
                                             path))
                      {:status   (:status response)
                       :method   method
                       :path     path
                       :response (safe-response response)})))))


(defn- login!
  []
  (let [{:keys [endpoint api-key api-secret] :as config} (nuvla-auth-config)
        response     (request! config
                               :post
                               "session"
                               {:ok-statuses #{201}
                                :body {:template {:href   "session-template/api-key"
                                                  :key    api-key
                                                  :secret api-secret}}})]
    {:endpoint     endpoint
     :cookie-header (session-cookie-header response)}))


(defn- retrieve-resource
  [client resource-id & [{:keys [not-found-ok?] :or {not-found-ok? false}}]]
  (let [ok-statuses (if not-found-ok? #{200 404} #{200})
        response    (request! client :get resource-id {:ok-statuses ok-statuses})]
    (when (= 200 (:status response))
      (:body response))))


(defn- query-resources
  [client resource-name filter-str & [{:keys [select last]
                                       :or   {last 100}}]]
  (let [query-params (cond-> {"filter" filter-str
                              "last"   (str last)}
                       (seq select) (assoc "select" (str/join "," select)))]
    (get-in (request! client :get resource-name {:query-params query-params})
            [:body :resources]
            [])))


(defn- query-first-resource
  [client resource-name filter-str & [options]]
  (first (query-resources client resource-name filter-str options)))


(defn- edit-resource!
  [client resource-id body]
  (-> (request! client :put resource-id {:ok-statuses #{200} :body body})
      :body))


(defn- create-resource!
  [client resource-name body]
  (-> (request! client :post resource-name {:ok-statuses #{201} :body body})
      :body))


(defn- delete-resource!
  [client resource-id]
  (-> (request! client :delete resource-id {:ok-statuses #{200}})
      :body))


(defn- run-action!
  [client resource-id action body]
  (-> (request! client
                :post
                (str resource-id "/" action)
                {:ok-statuses #{202}
                 :body        body})
      :body))


(defn- target-host-id
  [payload mec-deployment]
  (or (request-value payload
                     [:mecHostId]
                     [:mec-host-id]
                     [:selectedMECHostInfo :hostId]
                     [:selected-mec-host-info :host-id]
                     [:mecHostInformation :hostId]
                     [:mec-host-information :host-id])
      (:mec-host-id mec-deployment)
      (:nuvlabox mec-deployment)
      (:parent mec-deployment)))


(defn- resource-id->uuid
  [resource-id]
  (some-> resource-id (str/split #"/") last))


(defn- sanitize-path-segment
  [value]
  (let [sanitized (-> (or value "")
                      str/lower-case
                      (str/replace #"[^a-z0-9._-]+" "-")
                      (str/replace #"^-+" "")
                      (str/replace #"-+$" ""))]
    (if (str/blank? sanitized)
      "generated"
      sanitized)))


(defn- native-module-path
  [mec-module]
  (str native-module-project-path
       "/"
       (sanitize-path-segment
         (or (resource-id->uuid (:id mec-module))
             (:appDId (:content mec-module))
             (:path mec-module)
             (:name mec-module)))))


(defn- service-name
  [image idx]
  (let [preferred (or (:swImageName image)
                      (some-> (:swImage image) (str/split #"/") last (str/split #":") first))]
    (str (sanitize-path-segment preferred) "-" idx)))


(defn- docker-compose-string
  [sw-images]
  (str "version: '3.9'\n"
       "services:\n"
       (apply str
              (map-indexed
                (fn [idx {:keys [swImage] :as image}]
                  (format "  %s:\n    image: %s\n    restart: unless-stopped\n"
                          (service-name image (inc idx))
                          swImage))
                sw-images))))


(defn- resolve-mec-module
  [client payload mec-deployment]
  (let [module-ref (or (request-value payload [:appDId] [:app-d-id])
                       (get-in mec-deployment [:module :href])
                       (:module mec-deployment))
        module-id  (cond
                     (map? module-ref) (:href module-ref)
                     (string? module-ref) module-ref
                     :else nil)]
    (when-not module-id
      (throw (ex-info "Unable to resolve MEC module for backing deployment"
                      {:status         400
                       :payload        payload
                       :mec-deployment (:id mec-deployment)})))
    (or (when (and (map? module-ref) (:content module-ref)) module-ref)
        (retrieve-resource client module-id)
        (throw (ex-info "Referenced MEC module not found"
                        {:status    404
                         :module-id module-id})))))


(defn- module-author
  [mec-module mec-deployment]
  (or (get-in mec-module [:content :appProvider])
      (:created-by mec-module)
      (:owner mec-deployment)
      "group/nuvla-admin"))


(defn- native-module-body
  [mec-module mec-deployment]
  (let [sw-images (get-in mec-module [:content :swImageDescriptor])
        _         (when-not (seq sw-images)
                    (throw (ex-info "MEC module has no swImageDescriptor entries to translate"
                                    {:status        400
                                     :mec-module-id (:id mec-module)})))
        app-name  (or (get-in mec-module [:content :appName])
                      (:name mec-module)
                      "MEC Native Backing App")]
    {:name          app-name
     :description   (or (get-in mec-module [:content :appDescription])
                        (:description mec-module)
                        (str "Native backing module derived from " (:id mec-module)))
     :path          (native-module-path mec-module)
     :subtype       "application"
     :compatibility "docker-compose"
     :content       {:author         (module-author mec-module mec-deployment)
                     :commit         (str "Synchronized from " (:id mec-module))
                     :docker-compose (docker-compose-string sw-images)}}))


(defn- ensure-project!
  [client path]
  (if-let [project (query-first-resource client "module" (format "path='%s'" path))]
    (when (or (str/blank? (:name project))
              (str/blank? (:description project)))
      (edit-resource! client (:id project)
                      {:name        (or (:name project) native-module-project-name)
                       :description (or (:description project) native-module-project-description)}))
    (create-resource! client "module" {:path        path
                                       :name        native-module-project-name
                                       :description native-module-project-description
                                       :subtype     "project"})))


(defn- upsert-native-module!
  [client mec-module mec-deployment]
  (ensure-project! client native-module-project-path)
  (let [body     (native-module-body mec-module mec-deployment)
        existing (query-first-resource client "module"
                                       (format "path='%s'" (:path body)))]
    (if-let [module-id (:id existing)]
      (do
        (edit-resource! client module-id body)
        (retrieve-resource client module-id))
      (let [response  (create-resource! client "module" body)
            module-id (:resource-id response)]
        (when-not module-id
          (throw (ex-info "Failed to create native backing module"
                          {:status        500
                           :mec-module-id (:id mec-module)
                           :module-path   (:path body)})))
        (retrieve-resource client module-id)))))


(defn- resolve-native-parent-credential!
  [client host-id]
  (let [nuvlabox    (or (retrieve-resource client host-id)
                        (throw (ex-info "Target Nuvla Edge not found"
                                        {:status 404
                                         :host-id host-id})))
        isg-id      (:infrastructure-service-group nuvlabox)
        _           (when-not isg-id
                      (throw (ex-info "Target Nuvla Edge has no infrastructure-service-group"
                                      {:status 409
                                       :host-id host-id})))
        service     (or (query-first-resource client "infrastructure-service"
                                              (format "subtype='swarm' and parent='%s'" isg-id)
                                              {:select ["id" "parent"]})
                        (query-first-resource client "infrastructure-service"
                                              (format "parent='%s'" isg-id)
                                              {:select ["id" "parent"]}))
        service-id  (:id service)
        _           (when-not service-id
                      (throw (ex-info "No infrastructure service found for target Nuvla Edge"
                                      {:status 409
                                       :host-id host-id
                                       :isg-id  isg-id})))
        credential  (query-first-resource client "credential"
                                          (format "subtype='%s' and parent='%s'"
                                                  swarm-credential-subtype
                                                  service-id)
                                          {:select ["id" "parent" "subtype"]})
        cred-id     (:id credential)]
    (when-not cred-id
      (throw (ex-info "No swarm client credential found for target Nuvla Edge"
                      {:status     409
                       :host-id    host-id
                       :service-id service-id
                       :subtype    swarm-credential-subtype})))
    cred-id))


(defn- create-backing-deployment-body
  [mec-deployment native-module native-parent]
  (let [filtered-tags (some->> (:tags mec-deployment)
                               (remove #(= mec-tag %))
                               vec
                               not-empty)]
    (cond-> {:module {:href (:id native-module)}
             :acl    (:acl mec-deployment)
             :parent native-parent}
    (:name mec-deployment) (assoc :name (:name mec-deployment))
    (:description mec-deployment) (assoc :description (:description mec-deployment))
    filtered-tags (assoc :tags filtered-tags))))


(defn- app-instance-id
  [payload]
  (request-value payload [:appInstanceId] [:app-instance-id]))


(defn- resolve-backing-deployment
  [client app-instance-id]
  (let [resource (retrieve-resource client app-instance-id {:not-found-ok? true})]
    (cond
      (and resource (:mec-app-instance-id resource))
      resource

      (and resource (:mec-backing-deployment-id resource))
      (retrieve-resource client (:mec-backing-deployment-id resource))

      resource
      resource

      :else
      (throw (ex-info "Backing deployment not found"
                      {:status          404
                       :app-instance-id app-instance-id})))))


(defn- requested-operational-state
  [payload]
  (some-> (request-value payload [:changeStateTo] [:change-state-to])
          str
          str/upper-case))


(defn- start-or-stop-action
  [payload]
  (case (requested-operational-state payload)
    "STARTED" "start"
    "STOPPED" "stop"
    nil))


(defn create-backing-deployment!
  "Create a backing Nuvla deployment for a MEC app instance and persist explicit
   correlation identifiers on both deployments.

   Expected payload keys include:
   - `appInstanceId` / `app-instance-id`: MEC-facing deployment id
   - `appDId` / `app-d-id`: module id (falls back to the MEC deployment module)
   - `mecHostId` / `mec-host-id` or nested host fields (falls back to the MEC deployment host)"
  [mepm-id payload]
  (let [client              (login!)
        mec-app-instance-id (or (request-value payload [:appInstanceId] [:app-instance-id])
                                (throw (ex-info "Missing MEC app instance id for NUVLA_BACKED instantiate"
                                                {:status 400
                                                 :payload payload})))
        mec-deployment      (retrieve-resource client mec-app-instance-id)
        _                   (when-not mec-deployment
                              (throw (ex-info "MEC app instance deployment not found"
                                              {:status 404
                                               :mec-app-instance-id mec-app-instance-id})))
        host-id             (target-host-id payload mec-deployment)
        _                   (when-not host-id
                              (throw (ex-info "Unable to resolve target MEC host for backing deployment"
                                              {:status              409
                                               :mec-app-instance-id mec-app-instance-id})))
        mec-module          (resolve-mec-module client payload mec-deployment)
        native-module       (upsert-native-module! client mec-module mec-deployment)
        native-parent       (resolve-native-parent-credential! client host-id)
        create-body         (create-backing-deployment-body mec-deployment native-module native-parent)
        create-response     (create-resource! client "deployment" create-body)
        backing-id          (:resource-id create-response)]
    (when-not backing-id
      (throw (ex-info "Failed to create backing deployment"
                      {:status 500
                       :mec-app-instance-id mec-app-instance-id
                       :mepm-id mepm-id})))
    (let [correlation {:mec-app-instance-id       mec-app-instance-id
                       :mec-backing-deployment-id backing-id
                       :mec-backend-mode          backend-mode}
          correlation (cond-> correlation
                        mepm-id (assoc :mec-mepm-id mepm-id)
                        (:mec-mepm-name mec-deployment) (assoc :mec-mepm-name (:mec-mepm-name mec-deployment))
                        host-id (assoc :mec-host-id host-id))]
      (edit-resource! client mec-app-instance-id {:mec-backing-deployment-id backing-id})
      (edit-resource! client backing-id correlation)
      (let [start-response (run-action! client backing-id "start" nil)]
      {:mec-app-instance-id       mec-app-instance-id
       :mec-backing-deployment-id backing-id
       :mec-host-id               host-id
       :mec-mepm-id               mepm-id
       :mec-backend-mode          backend-mode
       :native-module-id          (:id native-module)
       :native-parent-id          native-parent
       :start-response            start-response
       :backing-deployment        (app-instance/with-mec-correlation
                                    (retrieve-resource client backing-id)
                                    correlation)}))))


(defn operate-backing-deployment!
  "Drive the backing deployment for a NUVLA_BACKED app instance to STARTED or
   STOPPED via native deployment actions."
  [mepm-id payload]
  (let [client           (login!)
        southbound-app-id (or (app-instance-id payload)
                              (throw (ex-info "Missing app instance id for NUVLA_BACKED operate"
                                              {:status 400
                                               :payload payload})))
        backing          (resolve-backing-deployment client southbound-app-id)
        action           (start-or-stop-action payload)]
    (when-not action
      (throw (ex-info "Unsupported changeStateTo for NUVLA_BACKED operate"
                      {:status 400
                       :payload payload})))
    (let [response (run-action! client (:id backing) action nil)]
      {:mepm-id               mepm-id
       :mec-app-instance-id   (:mec-app-instance-id backing)
       :backing-deployment-id (:id backing)
       :change-state-to       (requested-operational-state payload)
       :action                action
       :response              response
       :backing-deployment    (retrieve-resource client (:id backing))})))


(defn retrieve-backing-deployment!
  "Resolve and retrieve the backing deployment for a MEC-facing app instance id
   or for a backing deployment id itself."
  [app-instance-or-backing-id]
  (let [client (login!)]
    (resolve-backing-deployment client app-instance-or-backing-id)))


(defn retrieve-nuvla-resource!
  "Retrieve an arbitrary Nuvla resource by id through the configured reverse
   API client."
  [resource-id]
  (let [client (login!)]
    (retrieve-resource client resource-id)))


(defn query-nuvla-resources!
  "Query arbitrary Nuvla resources through the configured reverse API client."
  [resource-name filter-str & [options]]
  (let [client (login!)]
    (query-resources client resource-name filter-str options)))


(defn terminate-backing-deployment!
  "Delete the backing deployment when possible; otherwise request a stop so the
   MEPM can converge it toward termination."
  [mepm-id payload]
  (let [client           (login!)
        southbound-app-id (or (app-instance-id payload)
                              (throw (ex-info "Missing app instance id for NUVLA_BACKED terminate"
                                              {:status 400
                                               :payload payload})))
        backing          (resolve-backing-deployment client southbound-app-id)
        deletable?       (#{"CREATED" "STOPPED"} (:state backing))]
    (if deletable?
      (let [response (delete-resource! client (:id backing))]
        {:mepm-id               mepm-id
         :mec-app-instance-id   (:mec-app-instance-id backing)
         :backing-deployment-id (:id backing)
         :action                "delete"
         :response              response})
      (let [response (run-action! client (:id backing) "stop" nil)]
        {:mepm-id               mepm-id
         :mec-app-instance-id   (:mec-app-instance-id backing)
         :backing-deployment-id (:id backing)
         :action                "stop"
         :response              response
         :backing-deployment    (retrieve-resource client (:id backing))}))))
