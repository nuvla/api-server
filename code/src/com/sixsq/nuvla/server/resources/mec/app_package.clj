(ns com.sixsq.nuvla.server.resources.mec.app-package
  "ETSI MEC 010-2 Mm1 Application Package Management API
   
   Reference Point: Mm1 (OSS ↔ MEO)
   Standard: ETSI GS MEC 010-2 v4.1.1
   Canonical API tree: app_pkgm/v1
   
   The Mm1 reference point enables OSS to:
   - Query available application packages (GET /app_packages)
   - Get specific application package details (GET /app_packages/{appPkgId})
   - Onboard new application packages (POST /app_packages)
   - Delete application packages (DELETE /app_packages/{appPkgId})
   
   This implementation integrates with Nuvla's module catalog,
   treating modules as application packages."
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [clojure.walk :as walk]
    [clj-yaml.core :as yaml]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [jsonista.core :as j]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.mec.notification-dispatcher :as dispatcher]
    [com.sixsq.nuvla.server.resources.module-application-mec :as module-app-mec]
    [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
    [com.sixsq.nuvla.server.resources.spec.module-application-mec :as mec-spec]
    [com.sixsq.nuvla.server.util.response :as r]
    [ring.util.response :as rur])
  (:import
    (java.math BigInteger)
    (java.io ByteArrayOutputStream InputStream)
    (java.security MessageDigest)
    (java.util Base64)
    (java.util.zip ZipInputStream)))


(def ^:const resource-type "mec-app-package")

(def ^:const base-path "/mec/mm1/app_pkgm/v1/app_packages")

(def ^:private zip-content-type "application/zip")

(def ^:private accepted-package-content-types
  #{zip-content-type
    "application/octet-stream"
    "application/x-zip-compressed"})

(def ^:private package-content-encoding "base64")

(def ^:private package-artifact-fields
  [:packageContentData
   :packageContentEncoding
   :packageContentMediaType
   :packageContentFilename
   :packageContentSha256])

(def ^:private package-metadata-fields
  (conj package-artifact-fields
        :packageAppPkgPath
        :packageAppPkgVersion
        :packageOperationalState))

(def ^:private package-descriptor-entry-pattern #"(?i).*\.(json|ya?ml)$")


(defn- request-base-path
  [request]
  (or (:mec/base-path request) base-path))


(defn- normalize-app-pkg-id
  [app-pkg-id]
  (cond
    (not (string? app-pkg-id))
    app-pkg-id

    (str/includes? app-pkg-id "/")
    app-pkg-id

    :else
    (u/resource-id "module" app-pkg-id)))


(defn- onboarded-request?
  [request]
  (str/includes? (or (:uri request) "") "/onboarded_app_packages"))


(defn- public-appd-content
  [content]
  (apply dissoc content package-metadata-fields))


(defn- package-content-present?
  [content]
  (boolean (some (comp seq #(get content %))
                 package-artifact-fields)))


(defn- internal-app-package-path
  []
  (str "mec-apps/" (u/rand-uuid)))


(defn- package-version-or-default
  [app-pkg-version]
  (or (some-> app-pkg-version str/trim not-empty)
      "1.0.0"))


(defn- bytes->base64
  [^bytes content-bytes]
  (.encodeToString (Base64/getEncoder) content-bytes))


(defn- base64->bytes
  [encoded]
  (.decode (Base64/getDecoder) encoded))


(defn- sha256-hex
  [^bytes content-bytes]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") content-bytes)]
    (format "%064x" (BigInteger. 1 digest))))


(defn- input-stream->bytes
  [^InputStream input-stream]
  (with-open [in input-stream
              out (ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))


(defn- request-body->bytes
  [body]
  (cond
    (nil? body)
    nil

    (bytes? body)
    body

    (instance? InputStream body)
    (input-stream->bytes body)

    :else
    (throw (ex-info "Package content body must contain ZIP bytes"
                    {:status 400
                     :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                     :title "Bad Request"
                     :detail "Package content body must contain ZIP bytes"}))))


(defn- request-content-type
  [request]
  (some-> (or (get-in request [:headers "content-type"])
              (:content-type request))
          str/lower-case
          (str/split #";")
          first
          str/trim))

(defn- request-accept-type
  [request]
  (some-> (get-in request [:headers "accept"])
          str/lower-case
          (str/split #",")
          first
          (str/split #";")
          first
          str/trim))


(defn- zip-request?
  [request]
  (contains? accepted-package-content-types (request-content-type request)))


(defn- package-filename
  [app-pkg-id content]
  (or (:packageContentFilename content)
      (str (u/id->uuid (normalize-app-pkg-id app-pkg-id)) ".zip")))


(defn- zip-entry->bytes
  [^ZipInputStream zip-stream]
  (let [out (ByteArrayOutputStream.)]
    (io/copy zip-stream out)
    (.toByteArray out)))


(defn- canonicalize-package-descriptor
  [value]
  (walk/postwalk
    (fn [node]
      (cond
        (map? node)
        (into {} node)

        (and (sequential? node)
             (not (vector? node)))
        (vec node)

        :else
        node))
    value))


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
  ([app-pkg-id]
   (package-links app-pkg-id base-path))
  ([app-pkg-id route-base-path]
   {:self {:href (str route-base-path "/" app-pkg-id)}
    :appPkgContent {:href (str route-base-path "/" app-pkg-id "/package_content")}
    :appD {:href (str route-base-path "/" app-pkg-id "/appd")}}))


(defn onboarding-state
  [module]
  (if (package-content-present? (:content module))
    "ONBOARDED"
    "CREATED"))

(defn operational-state
  [module]
  (or (get-in module [:content :packageOperationalState]) "ENABLED"))


(defn ensure-mec-app-package!
  [request app-pkg-id]
  (let [normalized-app-pkg-id (normalize-app-pkg-id app-pkg-id)
        module (crud/retrieve-by-id-as-admin normalized-app-pkg-id)]
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
    (let [module-with-content (module-utils/retrieve-module-content module {:params {:uuid (u/id->uuid normalized-app-pkg-id)}})]
      (update module-with-content :content #(assoc % :appDId normalized-app-pkg-id)))))


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
  [project-path authn]
  (when (seq project-path)
    (when-let [parent-path (module-utils/get-parent-path project-path)]
      (when (seq parent-path)
        (ensure-project-path! parent-path authn)))
    (when-not (project-exists? project-path)
      (crud/add {:params {:resource-name "module"}
                 :body {:subtype "project"
                        :path project-path}
                 :nuvla/authn authn}))))


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


(defn- parse-package-descriptor-candidate
  [entry-name entry-bytes]
  (let [entry-name-lc (some-> entry-name str/lower-case)
        entry-text    (String. ^bytes entry-bytes "UTF-8")]
    (try
      (some-> (cond
                (str/ends-with? entry-name-lc ".json")
                (j/read-value entry-text j/keyword-keys-object-mapper)

                (or (str/ends-with? entry-name-lc ".yaml")
                    (str/ends-with? entry-name-lc ".yml"))
                (yaml/parse-string entry-text :keywords true)

                :else
                nil)
              canonicalize-package-descriptor)
      (catch Exception e
        (log/debug e "Ignoring unparsable package descriptor candidate" entry-name)
        nil))))


(defn- extract-package-appd
  [module content-bytes]
  (with-open [zip-stream (ZipInputStream. (io/input-stream content-bytes))]
    (loop [entry (.getNextEntry zip-stream)]
      (if-not entry
        nil
        (let [entry-name (.getName entry)
              entry-bytes (zip-entry->bytes zip-stream)
              _ (.closeEntry zip-stream)]
          (if (and (not (.isDirectory entry))
                   (re-matches package-descriptor-entry-pattern entry-name))
            (if-let [parsed-content (parse-package-descriptor-candidate entry-name entry-bytes)]
              (let [validated-appd (try
                                     (validate-package-content! module parsed-content)
                                     (catch clojure.lang.ExceptionInfo _
                                       nil))]
                (if validated-appd
                  (do
                    (log/info "Extracted AppD from package artifact entry" entry-name)
                    validated-appd)
                  (recur (.getNextEntry zip-stream))))
              (recur (.getNextEntry zip-stream)))
            (recur (.getNextEntry zip-stream))))))))


(defn update-package-content!
  [request app-pkg-id content]
  (let [module (ensure-mec-app-package! request app-pkg-id)
        normalized-content (validate-package-content! module content)
        existing-package-content (select-keys (:content module) package-metadata-fields)
        updated-module (-> module
                           (assoc :name (:appName normalized-content))
                           (assoc :description (:appDescription normalized-content))
                           (assoc :content (-> (merge normalized-content existing-package-content)
                                               (dissoc :packageAppPkgVersion))))]
    (crud/edit-by-id-as-admin app-pkg-id updated-module)
    (ensure-mec-app-package! request app-pkg-id)))


(defn update-package-zip-content!
  [request app-pkg-id body]
  (let [module        (ensure-mec-app-package! request app-pkg-id)
        content-bytes (request-body->bytes body)]
    (when-not (seq content-bytes)
      (throw (ex-info "Package content body must contain ZIP bytes"
                      {:status 400
                       :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                       :title "Bad Request"
                       :detail "Package content body must contain ZIP bytes"})))
    (let [extracted-appd (extract-package-appd module content-bytes)
          appd-content   (or extracted-appd
                             (public-appd-content (:content module)))
          existing-package-content (select-keys (:content module) package-metadata-fields)
          updated-module (-> module
                             (assoc :name (:appName appd-content))
                             (assoc :description (:appDescription appd-content))
                             (assoc :content (cond-> (merge appd-content
                                                            existing-package-content
                                                            {:packageContentData (bytes->base64 content-bytes)
                                                             :packageContentEncoding package-content-encoding
                                                             :packageContentMediaType (or (request-content-type request) zip-content-type)
                                                             :packageContentFilename (package-filename app-pkg-id (:content module))
                                                             :packageContentSha256 (sha256-hex content-bytes)})
                                               extracted-appd (dissoc :packageAppPkgVersion))))]
      (crud/edit-by-id-as-admin app-pkg-id updated-module)
      (ensure-mec-app-package! request app-pkg-id))))

(defn module->app-pkg-info
  "Converts a Nuvla module to MEC AppPkgInfo format (ETSI MEC 010-2 section 7.3.2.2)"
  ([module]
   (module->app-pkg-info module base-path))
  ([module route-base-path]
   (let [content (:content module)
         subtype (:subtype module)
         app-soft-version (or (:packageAppPkgVersion content)
                              (:appSoftVersion content)
                              (str (count (:versions module))))]
     {:id                 (:id module)
      :appPkgId           (:id module)
      :appDId             (or (:appDId content) (:id module))
      :appName            (or (:appName content) (:name module))
      :appProvider        (or (:appProvider content) (:parent-path module))
      :appSoftVersion     app-soft-version
      :appSoftwareVersion app-soft-version
      :appDVersion        (or (:appDVersion content) "1.0")
      :checksum           {:algorithm "SHA-256"
                           :hash      (or (:packageContentSha256 content)
                                          (:content-id module)
                                          "not-computed")}
      :softwareImages     (or (:swImageDescriptor content) [])
      :additionalArtifacts []
      :operationalState   (operational-state module)
      :usageState         (if (:published module) "IN_USE" "NOT_IN_USE")
      :onboardingState    (onboarding-state module)
      :mecInfo            (cond-> []
                            (:mecVersion content) (conj (:mecVersion content)))
      :appPkgPath         (or (:packageAppPkgPath content) (:path module))
      :moduletype         subtype
      :created            (:created module)
      :updated            (:updated module)
      :_links             (package-links (:id module) route-base-path)})))


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

(def ^:private supported-query-params
  #{"appPkgId"
    "appDId"
    "appName"
    "appProvider"
    "appSoftVersion"
    "operationalState"
    "usageState"
    "onboardingState"})

(defn- unsupported-query-params
  [params]
  (->> (keys params)
       (keep (fn [k]
               (let [param-name (cond
                                  (keyword? k) (name k)
                                  (string? k) k
                                  :else nil)]
                 (when (and param-name
                            (not (supported-query-params param-name)))
                   param-name))))
       distinct
       seq))


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
  (let [route-base-path (request-base-path request)]
    (try
      (let [params (:params request)
          onboarded-view? (onboarded-request? request)
          unsupported-params (unsupported-query-params params)
          _ (when unsupported-params
              (throw (ex-info "Unsupported query parameters"
                              {:status 400
                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                               :title "Bad Request"
                               :detail (str "Unsupported query parameter(s): "
                                            (str/join ", " unsupported-params))})))
          
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
                                       (module->app-pkg-info route-base-path))))
                            (filter #(if onboarded-view?
                                       (= "ONBOARDED" (:onboardingState %))
                                       (= "CREATED" (:onboardingState %))))
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
      
        (r/json-response app-packages))
      
      (catch clojure.lang.ExceptionInfo e
        (let [data (ex-data e)]
          (problem-response (or (:status data) 400)
                            (or (:title data) "Bad Request")
                            (or (:detail data) (.getMessage e))
                            (or (:type data) "about:blank"))))

      (catch Exception e
        (log/error e "Mm1: Error querying app_packages")
        (problem-response 500 "Internal Server Error" (.getMessage e))))))


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
  (let [route-base-path (request-base-path request)]
    (try
      (let [app-pkg-id (get-in request [:params :appPkgId])
          
            module (ensure-mec-app-package! request app-pkg-id)

            ;; Convert to AppPkgInfo
            app-pkg-info (module->app-pkg-info module route-base-path)]
      
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
        (problem-response 500 "Internal Server Error" (.getMessage e))))))


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
          external-app-pkg-path (some-> (:appPkgPath body) str/trim not-empty)
          internal-app-pkg-path (internal-app-package-path)
          user-defined-data (:userDefinedData body)
          parent-path (module-utils/get-parent-path internal-app-pkg-path)
          placeholder-version (package-version-or-default app-pkg-version)
          
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
          _ (ensure-project-path! parent-path (:nuvla/authn request))
          
          module-request {:params {:resource-name "module"}
                          :body {:name app-pkg-name
                                 :description (str "MEC Application Package: " app-pkg-name)
                                 :subtype "application_mec"
                                 :path internal-app-pkg-path
                                 :parent-path parent-path
                                 :versions [{:href (str app-pkg-name "/" placeholder-version)}]
                                 :published false
                                 :content (cond-> {:appName app-pkg-name
                                                   :appDescription (str "MEC Application Package: " app-pkg-name)
                                                   :appDId (str "module/" (u/rand-uuid))
                                                   :appProvider (or (:appProvider body) user-id)
                                                   :appSoftVersion placeholder-version
                                                   :appDVersion "3.2.1"
                                                   :mecVersion "2.2.1"
                                                   ;; Minimal required MEC AppD fields
                                                   :virtualComputeDescriptor {:virtualCpu {:numVirtualCpu 1}
                                                                              :virtualMemory {:virtualMemSize 1024}}
                                                   :swImageDescriptor [{:swImageName app-pkg-name
                                                                        :swImageVersion placeholder-version
                                                                        :containerFormat :DOCKER
                                                                        :swImage "sixsq/example-mec-app:1.0.0"}]
                                                   :virtualStorageDescriptor []
                                                   :appExtCpd []
                                                   :appServiceRequired []
                                                   :trafficRuleDescriptor []
                                                   :dnsRuleDescriptor []
                                                   :appFeatureRequired []
                                                   :packageAppPkgPath (or external-app-pkg-path internal-app-pkg-path)}
                                            (some-> app-pkg-version str/trim not-empty)
                                            (assoc :packageAppPkgVersion app-pkg-version)
                                            (some? user-defined-data)
                                            (assoc :userDefinedData user-defined-data))}
                          :nuvla/authn (:nuvla/authn request)}
          
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
      (dispatcher/dispatch-app-package-onboarding! app-pkg-info)
      
      (-> (json-response-status app-pkg-info 201)
          (rur/header "Location" (str base-path "/" module-id))))
    
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (log/warn e "Mm1: Bad request creating app_package"
                  {:message (.getMessage e)
                   :data    data})
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
    (let [app-pkg-id    (get-in request [:params :appPkgId])
          module        (ensure-mec-app-package! request app-pkg-id)
          app-pkg-info  (module->app-pkg-info module)
          normalized-app-pkg-id (:id module)
          
          ;; Check if package is in use
          _ (when (:published module)
              (throw (ex-info "Cannot delete application package in use"
                              {:status 409
                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/conflict"
                               :title "Conflict"
                               :detail "Application package is currently in use and cannot be deleted"})))
          
          ;; Delete the module
          delete-request {:params {:resource-name "module"
                                   :uuid (u/id->uuid normalized-app-pkg-id)}
                          :identity {:user "internal"
                                     :active-claim "internal"}
                          :nuvla/authn auth/internal-identity}
          _ (crud/delete delete-request)]
      
      (log/info "Mm1: Delete app_package" app-pkg-id)
      (dispatcher/dispatch-app-package-state-change! app-pkg-info "DELETION" (:operationalState app-pkg-info))
      
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


(def ^:private supported-operational-states
  #{"ENABLED" "DISABLED"})


(defn update-app-package
  "Update mutable application package fields for the supported subset.

   ETSI PKGM currently exercises `operationalState` updates through PATCH."
  [request]
  (try
    (let [app-pkg-id        (get-in request [:params :appPkgId])
          {:keys [operationalState] :as body} (:body request)
          module            (ensure-mec-app-package! request app-pkg-id)
          _                 (when-not (map? body)
                              (throw (ex-info "Patch body must be a JSON object"
                                              {:status 400
                                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                                               :title "Bad Request"
                                               :detail "Patch body must be a JSON object"})))
          _                 (when-not (contains? supported-operational-states operationalState)
                              (throw (ex-info "Invalid operationalState"
                                              {:status 400
                                               :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                                               :title "Bad Request"
                                               :detail "operationalState must be ENABLED or DISABLED"})))
          previous-state    (operational-state module)
          updated-module    (assoc-in module [:content :packageOperationalState] operationalState)
          _                 (crud/edit-by-id-as-admin (:id module) updated-module)
          refreshed-module  (ensure-mec-app-package! request app-pkg-id)
          updated-state     (operational-state refreshed-module)
          updated-info      (module->app-pkg-info refreshed-module)]
      (when (not= previous-state updated-state)
        (dispatcher/dispatch-app-package-state-change! updated-info
                                                       "OPERATIONAL_STATE"
                                                       previous-state))
      (log/info "Mm1: Updated app_package" app-pkg-id "operationalState" updated-state)
      (r/json-response {:operationalState updated-state}))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (problem-response (or (:status data) 400)
                          (or (:title data) "Bad Request")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    (catch Exception e
      (log/error e "Mm1: Error updating app_package")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


(defn get-app-package-appd
  "Get the AppD JSON descriptor for an onboarded package."
  [request]
  (try
    (let [app-pkg-id (get-in request [:params :appPkgId])
          module (ensure-mec-app-package! request app-pkg-id)
          appd (public-appd-content (:content module))]
      (log/info "Mm1: Get appd for app_package" app-pkg-id)
      (-> (r/json-response appd)
          (rur/content-type "application/json")))
    (catch clojure.lang.ExceptionInfo e
      (let [data (ex-data e)]
        (problem-response (or (:status data) 404)
                          (or (:title data) "Not Found")
                          (or (:detail data) (.getMessage e))
                          (or (:type data) "about:blank"))))
    (catch Exception e
      (log/error e "Mm1: Error getting appd")
      (problem-response 500 "Internal Server Error" (.getMessage e)))))


(defn get-app-package-content
  "Get package content for the supported subset.

   If a ZIP artifact has been uploaded for the package, return the persisted ZIP.
   Otherwise fall back to the legacy JSON AppD representation stored in the
   Nuvla module catalogue."
  [request]
  (try
    (let [accept-type (request-accept-type request)
          _          (when (and accept-type
                                (not= accept-type "*/*")
                                (not (contains? accepted-package-content-types accept-type)))
                       (throw (ex-info "Unsupported Accept header for package content"
                                       {:status 400
                                        :type "https://forge.etsi.org/rep/mec/gs010-2-app-pkg-lcm-api/problems/bad-request"
                                        :title "Bad Request"
                                        :detail "Only application/zip is supported for package_content retrieval"})))
          app-pkg-id (get-in request [:params :appPkgId])
          module (ensure-mec-app-package! request app-pkg-id)
          content (:content module)]
      (log/info "Mm1: Get package_content for app_package" app-pkg-id)
      (if (package-content-present? content)
        (-> (rur/response (base64->bytes (:packageContentData content)))
            (rur/content-type (or (:packageContentMediaType content) zip-content-type))
            (rur/header "Content-Disposition" (str "attachment; filename=\"" (package-filename app-pkg-id content) "\"")))
        (-> (r/json-response (public-appd-content content))
            (rur/content-type "application/json")
            (rur/header "Content-Disposition" (str "attachment; filename=\"" (str (u/id->uuid app-pkg-id) "-appd.json") "\"")))))
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

   The current implementation accepts either:
   - a JSON MEC AppD document, persisted as the package descriptor
   - a ZIP package artifact, persisted as base64-encoded bytes alongside the
     descriptor for POC storage in Elasticsearch."
  [request]
  (try
    (let [app-pkg-id      (get-in request [:params :appPkgId])
          body            (:body request)
          previous-module (ensure-mec-app-package! request app-pkg-id)
          previous-state  (operational-state previous-module)
          submit-request? (or (nil? body)
                              (and (string? body) (str/blank? body)))
          _               (when submit-request?
                            (log/info "Mm1: Accepted asynchronous package submission request for" app-pkg-id))
          updated-module  (when-not submit-request?
                            (if (zip-request? request)
                              (update-package-zip-content! request app-pkg-id body)
                              (update-package-content! request app-pkg-id body)))
          updated-info    (when updated-module
                            (module->app-pkg-info updated-module))]
      (when (and updated-info
                 (not= previous-state (:operationalState updated-info)))
        (dispatcher/dispatch-app-package-state-change! updated-info
                                                       "OPERATIONAL_STATE"
                                                       previous-state))
      (when-not submit-request?
        (log/info "Mm1: Updated package_content for app_package" app-pkg-id))
      {:status (if submit-request? 202 204)
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
                  :patch update-app-package
                  :delete delete-app-package}]
   ["/:appPkgId/appd" {:get get-app-package-appd}]
   ["/onboarded_app_packages" {:get query-app-packages}]
   ["/onboarded_app_packages/:appPkgId" {:get get-app-package
                                         :patch update-app-package
                                         :delete delete-app-package}]
   ["/onboarded_app_packages/:appPkgId/appd" {:get get-app-package-appd}]
   ["/onboarded_app_packages/:appPkgId/package_content" {:get get-app-package-content
                                                         :put put-app-package-content}]
   ["/:appPkgId/package_content" {:get get-app-package-content
                                  :put put-app-package-content}]])
