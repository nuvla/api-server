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
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.spec.module-application-mec :as mec-spec]
    [com.sixsq.nuvla.server.util.response :as r]
    [ring.util.response :as rur]))


(def ^:const resource-type "mec-app-package")


;;
;; Utility functions to map Nuvla modules to MEC AppPkgInfo
;;

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
     :operationalState "ENABLED"
     :usageState (if (:published module) "IN_USE" "NOT_IN_USE")
     :onboardingState "ONBOARDED"
     :appPkgPath (:path module)
     :moduletype (:subtype module)
     :created (:created module)
     :updated (:updated module)}))


(defn app-pkg-filter
  "Creates a filter to query MEC-capable modules (application_mec subtype)"
  [additional-filter]
  (let [base-filter "subtype='application_mec'"
        filter-str (if additional-filter
                     (str base-filter " and " additional-filter)
                     base-filter)]
    filter-str))


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
                    app-d-id (conj (str "content/appDId='" app-d-id "'"))
                    app-name (conj (str "content/appName='" app-name "' or name='" app-name "'"))
                    app-provider (conj (str "content/appProvider='" app-provider "'"))
                    app-soft-version (conj (str "content/appSoftVersion='" app-soft-version "'"))
                    (= usage-state "IN_USE") (conj "published=true")
                    (= usage-state "NOT_IN_USE") (conj "published=false"))
          
          filter-str (app-pkg-filter (when (seq filters)
                                       (clojure.string/join " and " filters)))
          
          ;; Query modules
          modules (crud/query-as-admin "module" {:cimi-params {:filter (parser/parse-cimi-filter filter-str)
                                                               :orderby [["created" :desc]]}})
          
          ;; Convert to AppPkgInfo format
          app-packages (map module->app-pkg-info (:resources modules))]
      
      (log/info "Mm1: Query app_packages, found" (count app-packages) "packages"
                "filters:" filter-str)
      
      (r/json-response {:AppPkgInfo app-packages
                        :_links {:self {:href "/mec/app_lcm/v2/app_packages"}}}))
    
    (catch Exception e
      (log/error e "Mm1: Error querying app_packages")
      (r/json-response {:type "about:blank"
                        :title "Internal Server Error"
                        :status 500
                        :detail (.getMessage e)}
                       500))))


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
          
          ;; Retrieve module
          module (crud/retrieve-by-id-as-admin app-pkg-id)
          
          ;; Check if module exists
          _ (when-not module
              (throw (ex-info "Application package not found"
                              {:status 404
                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/not-found"
                               :title "Not Found"
                               :detail (str "Application package " app-pkg-id " not found")})))
          
          ;; Convert to AppPkgInfo
          app-pkg-info (module->app-pkg-info module)]
      
      (log/info "Mm1: Get app_package" app-pkg-id)
      
      (r/json-response app-pkg-info))
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (log/warn "Mm1: App package not found:" (get-in request [:params :appPkgId]))
        (r/json-response {:type (or (:type data) "about:blank")
                          :title (or (:title data) "Not Found")
                          :status (or (:status data) 404)
                          :detail (or (:detail data) (.getMessage e))}
                         (or (:status data) 404))))
    
    (catch Exception e
      (log/error e "Mm1: Error getting app_package")
      (r/json-response {:type "about:blank"
                        :title "Internal Server Error"
                        :status 500
                        :detail (.getMessage e)}
                       500))))


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
          app-pkg-path (:appPkgPath body)
          user-defined-data (:userDefinedData body)
          
          ;; Validate required fields
          _ (when-not app-pkg-name
              (throw (ex-info "appPkgName is required"
                              {:status 400
                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                               :title "Bad Request"
                               :detail "appPkgName is required"})))
          
          ;; Create minimal MEC AppD module
          ;; This creates a placeholder module that can be updated with full AppD content later
          user-id (or (get-in request [:identity :user]) "internal")
          
          module-request {:params {:resource-name "module"}
                          :body {:name app-pkg-name
                                 :description (str "MEC Application Package: " app-pkg-name)
                                 :subtype "application_mec"
                                 :path (or app-pkg-path (str "mec-apps/" app-pkg-name))
                                 :parent-path (str "mec-apps/" (or (:appProvider body) user-id))
                                 :versions [{:href (str app-pkg-name "/" (or app-pkg-version "1.0.0"))}]
                                 :published false
                                 :content {:appName app-pkg-name
                                           :appDId (str "appd-" (u/rand-uuid))
                                           :appProvider (or (:appProvider body) user-id)
                                           :appSoftVersion (or app-pkg-version "1.0.0")
                                           :appDVersion "3.2.1"
                                           :mecVersion "2.2.1"
                                           ;; Minimal required MEC AppD fields
                                           :virtualComputeDescriptor [{:virtualComputeDescId "compute-1"
                                                                       :virtualCpu {:numVirtualCpu 1}
                                                                       :virtualMemory {:virtualMemSize 1024}}]
                                           :swImageDescriptor []
                                           :virtualStorageDescriptor []
                                           :appExtCpd []
                                           :appServiceRequired []
                                           :trafficRuleDescriptor []
                                           :dnsRuleDescriptor []
                                           :appFeatureRequired []
                                           ;; Store user-defined metadata
                                           :userDefinedData user-defined-data}}
                          :identity {:user user-id
                                     :active-claim user-id}}
          
          ;; Create the module
          create-response (crud/add module-request)
          module-id (get-in create-response [:body :resource-id])
          
          ;; Retrieve the created module to get full details
          module (crud/retrieve-by-id-as-admin module-id)
          
          ;; Convert to AppPkgInfo
          app-pkg-info (-> (module->app-pkg-info module)
                           (assoc :onboardingState "CREATED"
                                  :operationalState "DISABLED"
                                  :usageState "NOT_IN_USE"
                                  :_links {:self {:href (str "/mec/app_lcm/v2/app_packages/" module-id)}
                                           :appPkgContent {:href (str "/mec/app_lcm/v2/app_packages/" module-id "/package_content")}
                                           :appD {:href (str "/mec/app_lcm/v2/app_packages/" module-id "/appD")}}))]
      
      (log/info "Mm1: Created app_package" app-pkg-name "version" app-pkg-version "id" module-id)
      
      (-> (r/json-response app-pkg-info)
          (assoc :status 201)
          (rur/header "Location" (str "/mec/app_lcm/v2/app_packages/" module-id))))
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (log/warn "Mm1: Bad request creating app_package:" (.getMessage e))
        (r/json-response {:type (or (:type data) "about:blank")
                          :title (or (:title data) "Bad Request")
                          :status (or (:status data) 400)
                          :detail (or (:detail data) (.getMessage e))}
                         (or (:status data) 400))))
    
    (catch Exception e
      (log/error e "Mm1: Error creating app_package")
      (r/json-response {:type "about:blank"
                        :title "Internal Server Error"
                        :status 500
                        :detail (.getMessage e)}
                       500))))


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
          
          ;; Retrieve module to check if it exists
          module (crud/retrieve-by-id-as-admin app-pkg-id)
          
          ;; Check if module exists
          _ (when-not module
              (throw (ex-info "Application package not found"
                              {:status 404
                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/not-found"
                               :title "Not Found"
                               :detail (str "Application package " app-pkg-id " not found")})))
          
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
        (r/json-response {:type (or (:type data) "about:blank")
                          :title (or (:title data) "Error")
                          :status (or (:status data) 500)
                          :detail (or (:detail data) (.getMessage e))}
                         (or (:status data) 500))))
    
    (catch Exception e
      (log/error e "Mm1: Error deleting app_package")
      (r/json-response {:type "about:blank"
                        :title "Internal Server Error"
                        :status 500
                        :detail (.getMessage e)}
                       500))))


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
                  :delete delete-app-package}]])
