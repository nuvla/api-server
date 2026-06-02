(ns com.sixsq.nuvla.server.resources.module-application-mec
  "MEC 037 Application Descriptor (AppD) module subtype implementation
   
   Standard: ETSI GS MEC 037 v3.2.1
   
   This module type provides native MEC compliance for application descriptors,
   implementing the Mm9 (Package Management) reference point with standard
   ETSI MEC AppD format.
   
   Features:
   - Full MEC 037 AppD schema validation
   - Resource requirement specifications
   - MEC service dependencies
   - Traffic and DNS rule descriptors
   - Multi-architecture container support
   - Integration with MEC 010-2 lifecycle API"
  (:require
    [clojure.walk :as walk]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.resource-metadata :as md]
    [com.sixsq.nuvla.server.resources.spec.module-application-mec :as spec-mec]
    [com.sixsq.nuvla.server.util.metadata :as gen-md]))


;;
;; Constants
;;

(def ^:const subtype "application_mec")

(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def collection-acl {:query ["group/nuvla-admin"]
                     :add   ["group/nuvla-admin"]})


(def resource-acl {:owners ["group/nuvla-admin"]})

(def ^:private enum-string-keys
  #{:containerFormat
    :typeOfStorage
    :layerProtocol
    :addressType
    :iPAddressAssignment
    :serName
    :diskFormat
    :ipAddressType})

(def ^:private package-metadata-keys
  [:packageContentData
   :packageContentEncoding
   :packageContentMediaType
   :packageContentFilename
   :packageContentSha256
   :packageAppPkgPath
   :packageAppPkgVersion])


;;
;; Validation Functions
;;

(defn validate-appd-content
  "Validates MEC AppD content structure against ETSI MEC 037 spec"
  [content]
  (when-not (spec-mec/valid-mec-appd? content)
    (let [problems (spec-mec/mec-appd-problems content)]
      (throw (ex-info "Invalid MEC AppD content"
                      {:status 400
                       :problems problems
                       :explanation (with-out-str (spec-mec/explain-mec-appd content))}))))
  content)


(defn normalize-appd-content
  "Normalizes JSON-decoded enum values into the keyword form expected by the spec."
  [content]
  (walk/postwalk
    (fn [node]
      (if (map? node)
        (reduce (fn [m k]
                  (if (contains? m k)
                    (update m k #(if (string? %) (keyword %) %))
                    m))
                node
                enum-string-keys)
        node))
    content))


(defn validate-resource-requirements
  "Validates that resource requirements are reasonable"
  [content]
  (let [compute (:virtualComputeDescriptor content)
        cpu-count (get-in compute [:virtualCpu :numVirtualCpu])
        memory-mb (get-in compute [:virtualMemory :virtualMemSize])]
    
    ;; Warn if requirements are excessive
    (when (> cpu-count 64)
      (log/warn "MEC AppD requests excessive CPU:" cpu-count "cores"))
    
    (when (> memory-mb 262144) ;; 256GB
      (log/warn "MEC AppD requests excessive memory:" memory-mb "MB"))
    
    ;; Check storage requirements
    (doseq [storage (:virtualStorageDescriptor content)]
      (let [size-gb (:sizeOfStorage storage)]
        (when (> size-gb 5000)
          (log/warn "MEC AppD requests excessive storage:" size-gb "GB")))))
  
  content)


(defn validate-mec-services
  "Validates MEC service dependencies"
  [content]
  (let [services (:appServiceRequired content)]
    (doseq [service services]
      (let [ser-name (:serName service)
            version (:version service)]
        (log/info "MEC AppD requires service:" ser-name "version:" version)
        
        ;; Could validate against supported MEC services
        (when-not (contains? #{:rnis :location :ue-identity :bandwidth-management
                               :wlan-information :fixed-access-information
                               :traffic-management}
                             ser-name)
          (log/warn "Unknown MEC service requested:" ser-name)))))
  
  content)


(defn validate-container-images
  "Validates software image descriptors"
  [content]
  (let [images (:swImageDescriptor content)]
    (when (empty? images)
      (throw (ex-info "At least one software image is required"
                      {:status 400})))
    
    (doseq [image images]
      (let [sw-image (:swImage image)
            container-format (:containerFormat image)]
        
        ;; Validate image reference format
        (when-not (re-matches #"^[a-z0-9]+([\.\-][a-z0-9]+)*(/[a-z0-9]+([\.\-][a-z0-9]+)*)*:[a-zA-Z0-9\.\-_]+$"
                              sw-image)
          (throw (ex-info "Invalid container image reference format"
                          {:status 400
                           :swImage sw-image})))
        
        ;; Currently only support Docker
        (when-not (= :DOCKER container-format)
          (log/warn "Non-Docker container format may not be supported:" container-format)))))
  
  content)


(defn validate-traffic-rules
  "Validates traffic rule descriptors"
  [content]
  (let [rules (:trafficRuleDescriptor content)]
    (doseq [rule rules]
      (let [priority (:priority rule)]
        (when-not (<= 0 priority 255)
          (throw (ex-info "Traffic rule priority must be 0-255"
                          {:status 400
                           :trafficRuleId (:trafficRuleId rule)
                           :priority priority}))))))
  
  content)


(defn validate-dns-rules
  "Validates DNS rule descriptors"
  [content]
  (let [rules (:dnsRuleDescriptor content)]
    (doseq [rule rules]
      (let [domain (:domainName rule)
            ip (:ipAddress rule)
            ip-type (:ipAddressType rule)]
        
        ;; Validate IP address format matches type
        (when (= :IPV4 ip-type)
          (when-not (re-matches #"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$" ip)
            (throw (ex-info "Invalid IPv4 address format"
                            {:status 400
                             :dnsRuleId (:dnsRuleId rule)
                             :ipAddress ip}))))
        
        (when (= :IPV6 ip-type)
          (when-not (re-matches #"^([0-9a-fA-F]{0,4}:){7}[0-9a-fA-F]{0,4}$" ip)
            (throw (ex-info "Invalid IPv6 address format"
                            {:status 400
                             :dnsRuleId (:dnsRuleId rule)
                             :ipAddress ip})))))))
  
  content)


;;
;; Multi-method dispatching
;;

(def validate-fn (u/create-spec-validation-fn ::spec-mec/schema))
(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


(defmethod crud/add-acl resource-type
  [resource _request]
  (assoc resource :acl resource-acl))


;;
;; Resource Metadata Extraction
;;

(defn extract-resource-summary
  "Extracts resource requirements summary from MEC AppD"
  [content]
  (let [compute (:virtualComputeDescriptor content)
        storage (:virtualStorageDescriptor content)
        images (:swImageDescriptor content)]
    {:cpus (get-in compute [:virtualCpu :numVirtualCpu])
     :memory-mb (get-in compute [:virtualMemory :virtualMemSize])
     :storage-gb (reduce + 0 (map :sizeOfStorage storage))
     :images (count images)
     :mec-version (:mecVersion content)
     :requires-mec-services (vec (map :serName (:appServiceRequired content)))}))


(defn extract-deployment-info
  "Extracts deployment-relevant information from MEC AppD"
  [content]
  {:app-name (:appName content)
   :app-version (:appSoftVersion content)
   :provider (:appProvider content)
   :description (:appDescription content)
   :container-images (map #(select-keys % [:swImageName :swImageVersion :swImage :containerFormat])
                          (:swImageDescriptor content))
   :resource-requirements (extract-resource-summary content)
   :network-requirements {:external-connections (count (:appExtCpd content))
                          :traffic-rules (count (:trafficRuleDescriptor content))
                          :dns-rules (count (:dnsRuleDescriptor content))}
   :mec-services (map #(select-keys % [:serName :version])
                      (:appServiceRequired content))})


;;
;; CRUD Operations
;;

(def add-impl (std-crud/add-fn resource-type collection-acl resource-type))

(defn validate-appd-request
  [request]
  (let [body            (:body request)
        package-content (select-keys body package-metadata-keys)
        content         (normalize-appd-content (apply dissoc body package-metadata-keys))]
    (-> content
        validate-appd-content
        validate-resource-requirements
        validate-mec-services
        validate-container-images
        validate-traffic-rules
        validate-dns-rules)
    (assoc request :body (merge content package-content))))

(defmethod crud/add resource-type
  [request]
  (let [request (validate-appd-request request)
        content (:body request)
        _ (log/info "Creating MEC AppD module")
        response (add-impl request)
        module-id (get-in response [:body :resource-id])]
    
    ;; Log deployment info for monitoring
    (when module-id
      (let [deploy-info (extract-deployment-info content)]
        (log/info "MEC AppD module created:"
                  "id:" module-id
                  "app:" (:app-name deploy-info)
                  "version:" (:app-version deploy-info)
                  "cpus:" (get-in deploy-info [:resource-requirements :cpus])
                  "memory:" (get-in deploy-info [:resource-requirements :memory-mb]) "MB"
                  "services:" (vec (map :serName (:mec-services deploy-info))))))
    
    response))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-type))


(defmethod crud/edit resource-type
  [request]
  (-> request
      validate-appd-request
      edit-impl))


(def delete-impl (std-crud/delete-fn resource-type))


(defmethod crud/delete resource-type
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-type collection-acl collection-type))


(defmethod crud/query resource-type
  [request]
  (query-impl request))


;;
;; Helper Functions for Integration
;;

(defn appd->deployment-params
  "Converts MEC AppD to deployment parameters for MEPM via Mm3"
  [module-id content]
  (let [compute (:virtualComputeDescriptor content)
        images (:swImageDescriptor content)
        primary-image (first images)]
    {:appDId module-id
     :appName (:appName content)
     :appProvider (:appProvider content)
     :appSoftVersion (:appSoftVersion content)
     :virtualComputeDescriptor compute
     :swImageDescriptor images
     :containerImage (:swImage primary-image)
     :containerFormat (name (:containerFormat primary-image))
     :appServiceRequired (vec (map #(select-keys % [:serName :version])
                                   (:appServiceRequired content)))
     :trafficRuleDescriptor (:trafficRuleDescriptor content)
     :dnsRuleDescriptor (:dnsRuleDescriptor content)}))


(defn check-mec-compatibility
  "Checks if MEC AppD is compatible with target MEPM capabilities"
  [content mepm-capabilities]
  (let [required-version (:mecVersion content)
        mepm-version (:mecVersion mepm-capabilities)
        required-services (set (map :serName (:appServiceRequired content)))
        available-services (set (:availableServices mepm-capabilities))]
    
    {:compatible? (and (>= (compare mepm-version required-version) 0)
                       (clojure.set/subset? required-services available-services))
     :version-match? (>= (compare mepm-version required-version) 0)
     :services-match? (clojure.set/subset? required-services available-services)
     :missing-services (clojure.set/difference required-services available-services)}))


;;
;; Initialization
;;

(def resource-metadata (gen-md/generate-metadata ::ns ::spec-mec/schema))

(defn initialize
  []
  (log/info "Initializing MEC 037 AppD module subtype:" subtype)
  (std-crud/initialize resource-type ::spec-mec/schema)
  (md/register resource-metadata))
