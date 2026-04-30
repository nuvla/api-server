(ns com.sixsq.nuvla.server.resources.mec.app-package
  "ETSI MEC 010-2 Mm1 Application Package Management API
   
   Reference Point: Mm1 (OSS ↔ MEO)
   Standard: ETSI GS MEC 010-2 v2.2.1
   Sections: 7.3.1, 7.3.2
   
   The Mm1 reference point enables OSS to:
   - Query available application packages (GET /app_packages)
   - Get specific application package details (GET /app_packages/{appPkgId})
   - Onboard new application packages (POST /app_packages)
   - Delete application packages (DELETE /app_packages/{appPkgId})
   
   This implementation integrates with Nuvla's module catalog,
   treating modules as application packages."
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.module-application-mec :as module-app-mec]
    [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
    [com.sixsq.nuvla.server.resources.spec.module-application-mec :as mec-spec]
    [com.sixsq.nuvla.server.util.response :as r]
    [ring.util.response :as rur]))


(def ^:const resource-type "mec-app-package")

(def ^:const base-path "/mec/app_lcm/v2/app_packages")


;;
;; Utility functions to map Nuvla modules to MEC AppPkgInfo
;;

(defn json-response-status
  [body status]
  (-> (r/json-response body)
      (assoc :status status)))


(defn problem-response
  ([status title detail]
   (problem-response status title detail "about:blank"))
  ([status title detail type]
   (json-response-status {:type type
                          :title title
                          :status status
                          :detail detail}
                         status)))


(defn package-links
  [app-pkg-id]
  {:self {:href (str base-path "/" app-pkg-id)}
   :appPkgContent {:href (str base-path "/" app-pkg-id "/package_content")}
   :appD {:href (str base-path "/" app-pkg-id "/appD")}})


(defn onboarding-state
  [module]
  (if (get-in module [:content :appDId])
    "ONBOARDED"
    "CREATED"))


(defn operational-state
  [module]
  (if (= "ONBOARDED" (onboarding-state module))
    "ENABLED"
    "DISABLED"))


(defn ensure-mec-app-package!
  [request app-pkg-id]
  (let [module (crud/retrieve-by-id-as-admin app-pkg-id)]
    (when-not module
      (throw (ex-info "Application package not found"
                      {:status 404
                       :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/not-found"
                       :title "Not Found"
                       :detail (str "Application package " app-pkg-id " not found")})))
    (when-not (= "application_mec" (:subtype module))
      (throw (ex-info "Application package not found"
                      {:status 404
                       :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/not-found"
                       :title "Not Found"
                       :detail (str "Application package " app-pkg-id " not found")})))
    (let [module-with-content (module-utils/retrieve-module-content module {:params {:uuid (u/id->uuid app-pkg-id)}})]
      (update module-with-content :content #(assoc % :appDId app-pkg-id)))))


(defn sync-appd-id!
  [module]
  (let [module-id (:id module)
        content (:content module)]
    (if (= module-id (:appDId content))
      module
      (let [updated-module (assoc module :content (assoc content :appDId module-id))]
        (crud/edit-by-id-as-admin module-id updated-module)
        (assoc updated-module :content (assoc content :appDId module-id))))))


(defn project-exists?
  [project-path]
  (when (seq project-path)
    (let [[_ resources] (crud/query-as-admin "module" {:cimi-params {:filter (parser/parse-cimi-filter (str "path='" project-path "'"))}})]
      (boolean (some #(= "project" (:subtype %)) resources)))))


(defn ensure-project-path!
  [project-path]
  (when (seq project-path)
    (when-let [parent-path (module-utils/get-parent-path project-path)]
      (when (seq parent-path)
        (ensure-project-path! parent-path)))
    (when-not (project-exists? project-path)
      (crud/add {:params {:resource-name "module"}
                 :body {:subtype "project"
                        :path project-path}
                 :nuvla/authn auth/internal-identity}))))


(defn normalize-package-content
  [module content]
  (-> content
      module-app-mec/normalize-appd-content
      (assoc :appDId (:id module))
      (update :appName #(or % (:name module)))
      (update :appDescription #(or % (:description module)))
      (update :appProvider #(or % (get-in module [:content :appProvider]) (:parent-path module)))))


(defn validate-package-content!
  [module content]
  (let [normalized-content (normalize-package-content module content)]
    (when-not (map? content)
      (throw (ex-info "Package content body must be a JSON object"
                      {:status 400
                       :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                       :title "Bad Request"
                       :detail "Package content body must be a JSON object"})))
    (when-not (mec-spec/valid-mec-appd? normalized-content)
      (throw (ex-info "Invalid MEC AppD content"
                      {:status 400
                       :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                       :title "Bad Request"
                       :detail "Package content is not a valid MEC AppD descriptor"
                       :problems (mec-spec/mec-appd-problems normalized-content)})))
    normalized-content))


(defn update-package-content!
  [request app-pkg-id content]
  (let [module (ensure-mec-app-package! request app-pkg-id)
        normalized-content (validate-package-content! module content)
        updated-module (-> module
                           (assoc :name (:appName normalized-content))
                           (assoc :description (:appDescription normalized-content))
                           (assoc :content normalized-content))]
    (crud/edit-by-id-as-admin app-pkg-id updated-module)
    (ensure-mec-app-package! request app-pkg-id)))

(defn module->app-pkg-info
  "Converts a Nuvla module to MEC AppPkgInfo format (ETSI MEC 010-2 section 7.3.2.2)"
  [module]
  (let [content (:content module)
        subtype (:subtype module)]
    {:id (:id module)
     :appPkgId (:id module)
     :appDId (or (:appDId content) (:id module))
     :appName (or (:appName content) (:name module))
     :appProvider (or (:appProvider content) (:parent-path module))
     :appSoftVersion (or (:appSoftVersion content) 
                         (str (count (:versions module))))
     :appDVersion (or (:appDVersion content) "1.0")
     :checksum {:algorithm "SHA-256"
                :hash (or (:content-id module) "not-computed")}
     :operationalState (operational-state module)
     :usageState (if (:published module) "IN_USE" "NOT_IN_USE")
     :onboardingState (onboarding-state module)
     :appPkgPath (:path module)
     :moduletype (:subtype module)
     :created (:created module)
     :updated (:updated module)
     :_links (package-links (:id module))}))


(defn app-pkg-filter
  "Creates a filter to query MEC-capable modules (application_mec subtype)"
  [additional-filter]
  (let [base-filter "subtype='application_mec'"
        filter-str (if additional-filter
                     (str base-filter " and " additional-filter)
                     base-filter)]
    filter-str))


(defn matches-app-package-filters?
  [app-pkg-info {:keys [appPkgId appDId appName appProvider appSoftVersion operationalState usageState onboardingState]}]
  (and (or (nil? appPkgId) (= appPkgId (:appPkgId app-pkg-info)))
       (or (nil? appDId) (= appDId (:appDId app-pkg-info)))
       (or (nil? appName) (= appName (:appName app-pkg-info)))
       (or (nil? appProvider) (= appProvider (:appProvider app-pkg-info)))
       (or (nil? appSoftVersion) (= appSoftVersion (:appSoftVersion app-pkg-info)))
       (or (nil? operationalState) (= operationalState (:operationalState app-pkg-info)))
       (or (nil? usageState) (= usageState (:usageState app-pkg-info)))
       (or (nil? onboardingState) (= onboardingState (:onboardingState app-pkg-info)))))


;;
;; GET /app_packages - Query application packages
;; ETSI MEC 010-2 section 7.3.1.3.1
;;

(defn query-app-packages
  "Query application packages with optional filters
   
   Query parameters (per ETSI MEC 010-2):
   - appPkgId: Application package identifier
   - appDId: Application descriptor identifier  
   - appName: Application name
   - appProvider: Application provider
   - appSoftVersion: Application software version
   - operationalState: Operational state (ENABLED/DISABLED)
   - usageState: Usage state (IN_USE/NOT_IN_USE)
   - onboardingState: Onboarding state (CREATED/UPLOADING/PROCESSING/ONBOARDED)
   
   Returns: AppPkgInfo[] (section 7.3.2.2)"
  [request]
  (try
    (let [params (:params request)
          
          ;; Extract MEC query parameters
          app-pkg-id (:appPkgId params)
          app-d-id (:appDId params)
          app-name (:appName params)
          app-provider (:appProvider params)
          app-soft-version (:appSoftVersion params)
          operational-state (:operationalState params)
          usage-state (:usageState params)
          onboarding-state (:onboardingState params)
          
          ;; Build Nuvla filter from MEC parameters
          filters (cond-> []
                    app-pkg-id (conj (str "id='" app-pkg-id "'"))
                    (= usage-state "IN_USE") (conj "published=true")
                    (= usage-state "NOT_IN_USE") (conj "published=false"))
          
          filter-str (app-pkg-filter (when (seq filters)
                                       (str/join " and " filters)))
          
          ;; Query modules
          [_ modules] (crud/query-as-admin "module" {:cimi-params {:filter (parser/parse-cimi-filter filter-str)
                                                                   :orderby [["created" :desc]]}})
          
          ;; Convert to AppPkgInfo format
          app-packages (->> modules
                            (map (fn [module]
                                   (-> module
                                       (module-utils/retrieve-module-content {:params {:uuid (u/id->uuid (:id module))}})
                                       module->app-pkg-info)))
                            (filter #(matches-app-package-filters? % {:appPkgId app-pkg-id
                                                                      :appDId app-d-id
                                                                      :appName app-name
                                                                      :appProvider app-provider
                                                                      :appSoftVersion app-soft-version
                                                                      :operationalState operational-state
                                                                      :usageState usage-state
                                                                      :onboardingState onboarding-state})))]
      
      (log/info "Mm1: Query app_packages, found" (count app-packages) "packages"
                "filters:" filter-str)
      
      (r/json-response {:AppPkgInfo app-packages
                        :_links {:self {:href base-path}}}))
    
    (catch Exception e
      (log/error e "Mm1: Error querying app_packages")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


;;
;; GET /app_packages/{appPkgId} - Get specific application package
;; ETSI MEC 010-2 section 7.3.1.3.2
;;

(defn get-app-package
  "Get information about a specific application package
   
   Path parameter:
   - appPkgId: Application package identifier (module ID)
   
   Returns: AppPkgInfo (section 7.3.2.2)"
  [request]
  (try
    (let [app-pkg-id (get-in request [:params :appPkgId])
          
          module (ensure-mec-app-package! request app-pkg-id)

          ;; Convert to AppPkgInfo
          app-pkg-info (module->app-pkg-info module)]
      
      (log/info "Mm1: Get app_package" app-pkg-id)
      
      (r/json-response app-pkg-info))
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (log/warn "Mm1: App package not found:" (get-in request [:params :appPkgId]))
        (problem-response (or (:status data) 404)
                          (or (:title data) "Not Found")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    
    (catch Exception e
      (log/error e "Mm1: Error getting app_package")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


;;
;; POST /app_packages - Onboard application package
;; ETSI MEC 010-2 section 7.3.1.3.3
;;

(defn create-app-package
  "Onboard a new application package
   
   Request body: CreateAppPkg (section 7.3.2.3)
   - appPkgName: Name of the application package
   - appPkgVersion: Version of the application package
   - appPkgPath: Optional path where package is stored
   - userDefinedData: Optional key-value pairs for user metadata
   
   Returns: AppPkgInfo (section 7.3.2.2)"
  [request]
  (try
    (let [body (:body request)
          app-pkg-name (:appPkgName body)
          app-pkg-version (:appPkgVersion body)
          app-pkg-path (or (:appPkgPath body) (str "mec-apps/" app-pkg-name))
          user-defined-data (:userDefinedData body)
          parent-path (module-utils/get-parent-path app-pkg-path)
          
          ;; Validate required fields
          _ (when-not app-pkg-name
              (throw (ex-info "appPkgName is required"
                              {:status 400
                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                               :title "Bad Request"
                               :detail "appPkgName is required"})))
          
          ;; Create minimal MEC AppD module
          ;; This creates a placeholder module that can be updated with full AppD content later
          user-id (or (auth/current-user-id request) "internal")
          _ (ensure-project-path! parent-path)
          
          module-request {:params {:resource-name "module"}
                          :body {:name app-pkg-name
                                 :description (str "MEC Application Package: " app-pkg-name)
                                 :subtype "application_mec"
                                 :path app-pkg-path
                                 :parent-path parent-path
                                 :versions [{:href (str app-pkg-name "/" (or app-pkg-version "1.0.0"))}]
                                 :published false
                                 :content {:appName app-pkg-name
                                           :appDescription (str "MEC Application Package: " app-pkg-name)
                                           :appDId (str "module/" (u/rand-uuid))
                                           :appProvider (or (:appProvider body) user-id)
                                           :appSoftVersion (or app-pkg-version "1.0.0")
                                           :appDVersion "3.2.1"
                                           :mecVersion "2.2.1"
                                           ;; Minimal required MEC AppD fields
                                           :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 1}
                                                                      :virtualMemory {:virtualMemSize 1024}}
                                           :swImageDescriptor [{:swImageName app-pkg-name
                                                                :swImageVersion (or app-pkg-version "1.0.0")
                                                                :containerFormat :DOCKER
                                                                :swImage "sixsq/example-mec-app:1.0.0"}]
                                           :virtualStorageDescriptor []
                                           :appExtCpd []
                                           :appServiceRequired []
                                           :trafficRuleDescriptor []
                                           :dnsRuleDescriptor []
                                           :appFeatureRequired []
                                           ;; Store user-defined metadata
                                           :userDefinedData user-defined-data}}
                          :nuvla/authn auth/internal-identity}
          
          ;; Create the module
          create-response (crud/add module-request)
          module-id (get-in create-response [:body :resource-id])
          
          ;; Retrieve the created module to get full details
          module (->> module-id
                      (ensure-mec-app-package! request)
                      sync-appd-id!)
          
          ;; Convert to AppPkgInfo
          app-pkg-info (module->app-pkg-info module)]
      
      (log/info "Mm1: Created app_package" app-pkg-name "version" app-pkg-version "id" module-id)
      
      (-> (json-response-status app-pkg-info 201)
          (rur/header "Location" (str "/mec/app_lcm/v2/app_packages/" module-id))))
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (log/warn "Mm1: Bad request creating app_package:" (.getMessage e))
        (problem-response (or (:status data) 400)
                          (or (:title data) "Bad Request")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    
    (catch Exception e
      (log/error e "Mm1: Error creating app_package")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


;;
;; DELETE /app_packages/{appPkgId} - Delete application package
;; ETSI MEC 010-2 section 7.3.1.3.4
;;

(defn delete-app-package
  "Delete an application package
   
   Path parameter:
   - appPkgId: Application package identifier (module ID)
   
   Returns: 204 No Content on success"
  [request]
  (try
    (let [app-pkg-id (get-in request [:params :appPkgId])
          
          module (ensure-mec-app-package! request app-pkg-id)
          
          ;; Check if package is in use
          _ (when (:published module)
              (throw (ex-info "Cannot delete application package in use"
                              {:status 409
                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/conflict"
                               :title "Conflict"
                               :detail "Application package is currently in use and cannot be deleted"})))
          
          ;; Delete the module
          delete-request {:params {:resource-name "module"
                                   :uuid (u/id->uuid app-pkg-id)}
                          :identity {:user "internal"
                                     :active-claim "internal"}
                          :nuvla/authn auth/internal-identity}
          _ (crud/delete delete-request)]
      
      (log/info "Mm1: Delete app_package" app-pkg-id)
      
      {:status 204
       :body nil})
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (log/warn "Mm1: Error deleting app_package:" (.getMessage e))
        (problem-response (or (:status data) 500)
                          (or (:title data) "Error")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    
    (catch Exception e
      (log/error e "Mm1: Error deleting app_package")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


(defn get-app-package-appd
  "Get the AppD JSON descriptor for an onboarded package."
  [request]
  (try
    (let [app-pkg-id (get-in request [:params :appPkgId])
          module (ensure-mec-app-package! request app-pkg-id)
          appd (:content module)]
      (log/info "Mm1: Get appD for app_package" app-pkg-id)
      (-> (r/json-response appd)
          (rur/content-type "application/json")))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (problem-response (or (:status data) 404)
                          (or (:title data) "Not Found")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    (catch Exception e
      (log/error e "Mm1: Error getting appD")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


(defn get-app-package-content
  "Get package content for the supported subset.

   For the current MVP subset, the onboarded package content is the persisted
   AppD JSON descriptor stored in the Nuvla module catalogue."
  [request]
  (try
    (let [app-pkg-id (get-in request [:params :appPkgId])
          module (ensure-mec-app-package! request app-pkg-id)
          filename (str (u/id->uuid app-pkg-id) "-appd.json")]
      (log/info "Mm1: Get package_content for app_package" app-pkg-id)
      (-> (r/json-response (:content module))
          (rur/content-type "application/json")
          (rur/header "Content-Disposition" (str "attachment; filename=\"" filename "\""))))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (problem-response (or (:status data) 404)
                          (or (:title data) "Not Found")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    (catch Exception e
      (log/error e "Mm1: Error getting package_content")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


(defn put-app-package-content
  "Upload package content for the supported subset.

   The current MVP accepts a JSON MEC AppD document and persists it as the
   onboarded package content associated with the module-backed package."
  [request]
  (try
    (let [app-pkg-id (get-in request [:params :appPkgId])
          body (:body request)
          _ (update-package-content! request app-pkg-id body)]
      (log/info "Mm1: Updated package_content for app_package" app-pkg-id)
      {:status 204
       :body nil})
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (problem-response (or (:status data) 400)
                          (or (:title data) "Bad Request")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    (catch Exception e
      (log/error e "Mm1: Error putting package_content")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


;;
;; Route handlers
;;

(defn routes
  "Mm1 Application Package Management routes"
  []
  ["/app_packages"
   ["" {:get query-app-packages
        :post create-app-package}]
   ["/:appPkgId" {:get get-app-package
                  :delete delete-app-package}]
   ["/:appPkgId/appD" {:get get-app-package-appd}]
   ["/:appPkgId/package_content" {:get get-app-package-content
                                  :put put-app-package-content}]])
