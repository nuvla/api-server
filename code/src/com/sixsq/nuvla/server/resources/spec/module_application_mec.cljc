(ns com.sixsq.nuvla.server.resources.spec.module-application-mec
  "Clojure spec for MEC 037 Application Descriptor (AppD) module subtype
   
   Standard: ETSI GS MEC 037 v3.2.1
   Purpose: Define MEC-native application descriptors for Nuvla module catalog
   
   This spec defines the structure for MEC applications following the ETSI MEC 037
   Application Descriptor format, enabling native MEC compliance for the Mm9
   (Package Management) reference point."
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]))


;;
;; MEC 037 AppD Core Attributes
;;

(s/def ::appDId
  (s/and string? #(re-matches #"^module/[a-z0-9]+(-[a-z0-9]+)*$" %)))

(s/def ::appDVersion
  (s/and string? #(re-matches #"^\d+\.\d+(\.\d+)?$" %)))

(s/def ::appName
  (s/and string? #(<= 1 (count %) 100)))

(s/def ::appProvider
  (s/and string? #(<= 1 (count %) 100)))

(s/def ::appSoftVersion
  (s/and string? #(re-matches #"^\d+\.\d+(\.\d+)?$" %)))

(s/def ::mecVersion
  (s/and string? #(re-matches #"^\d+\.\d+\.\d+$" %)))

(s/def ::appInfoName
  (s/and string? #(<= 1 (count %) 200)))

(s/def ::appDescription
  (s/and string? #(<= 1 (count %) 1000)))


;;
;; Virtual Compute Descriptor
;;

(s/def ::numVirtualCpu pos-int?)

(s/def ::virtualCpuClock
  (s/and number? pos?))

(s/def ::virtualCpuPinning
  #{:STATIC :DYNAMIC})

(s/def ::virtualCpu
  (s/keys :req-un [::numVirtualCpu]
          :opt-un [::virtualCpuClock ::virtualCpuPinning]))

(s/def ::virtualMemSize
  (s/and pos-int? #(<= 256 % 524288))) ;; 256MB to 512GB in MB

(s/def ::numaEnabled boolean?)

(s/def ::virtualMemory
  (s/keys :req-un [::virtualMemSize]
          :opt-un [::numaEnabled]))

(s/def ::computeId
  (s/and string? #(<= 1 (count %) 100)))

(s/def ::logicalNode
  (s/and string? #(<= 1 (count %) 100)))

(s/def ::virtualComputeDescriptor
  (s/keys :req-un [::virtualCpu ::virtualMemory]
          :opt-un [::computeId ::logicalNode]))


;;
;; Virtual Storage Descriptor
;;

(s/def ::typeOfStorage
  #{:BLOCK :OBJECT :FILE})

(s/def ::sizeOfStorage
  (s/and pos-int? #(<= 1 % 10000))) ;; 1GB to 10TB in GB

(s/def ::rdmaEnabled boolean?)

(s/def ::id
  (s/and string? #(<= 1 (count %) 100)))

(s/def ::virtualStorageDescriptor-item
  (s/keys :req-un [::typeOfStorage ::sizeOfStorage]
          :opt-un [::rdmaEnabled ::id]))

(s/def ::virtualStorageDescriptor
  (s/coll-of ::virtualStorageDescriptor-item :kind vector?))


;;
;; Software Image Descriptor
;;

(s/def ::swImageName
  (s/and string? #(<= 1 (count %) 200)))

(s/def ::swImageVersion
  (s/and string? #(re-matches #"^\d+\.\d+(\.\d+)?$" %)))

(s/def ::containerFormat
  #{:DOCKER :ACI :OCI})

(s/def ::swImage
  (s/and string? #(re-matches #"^[a-z0-9]+([\.\-][a-z0-9]+)*(/[a-z0-9]+([\.\-][a-z0-9]+)*)*:[a-zA-Z0-9\.\-_]+$" %)))

(s/def ::minDisk
  (s/and pos-int? #(<= 1 % 1000))) ;; 1GB to 1TB in GB

(s/def ::minRam
  (s/and pos-int? #(<= 256 % 524288))) ;; 256MB to 512GB in MB

(s/def ::diskFormat
  #{:RAW :QCOW2 :VDI :VMDK :VHD})

(s/def ::operatingSystem
  (s/and string? #(<= 1 (count %) 100)))

(s/def ::supportedVirtualisationEnvironment
  (s/coll-of string? :kind vector? :min-count 1))

(s/def ::swImageDescriptor-item
  (s/keys :req-un [::swImageName ::swImageVersion ::containerFormat ::swImage]
          :opt-un [::minDisk ::minRam ::diskFormat ::operatingSystem
                   ::supportedVirtualisationEnvironment]))

(s/def ::swImageDescriptor
  (s/coll-of ::swImageDescriptor-item :kind vector? :min-count 1))


;;
;; External Connection Point Descriptor
;;

(s/def ::cpdId
  (s/and string? #(re-matches #"^[a-z0-9\-]+$" %)))

(s/def ::layerProtocol
  #{:TCP :UDP :HTTP :HTTPS :WEBSOCKET})

(s/def ::addressType
  #{:IPV4 :IPV6 :MAC})

(s/def ::iPAddressAssignment
  #{:DYNAMIC :STATIC})

(s/def ::floatingIpActivated boolean?)

(s/def ::numberOfIpAddress pos-int?)

(s/def ::logicalNodeRequirements
  (s/keys :opt-un [::logicalNode]))

(s/def ::l3AddressData
  (s/keys :req-un [::addressType ::iPAddressAssignment]
          :opt-un [::floatingIpActivated ::numberOfIpAddress]))

(s/def ::nicIoRequirements
  (s/keys :opt-un [::logicalNodeRequirements]))

(s/def ::bandwidthRequirements
  (s/and pos-int? #(<= 1 % 100000))) ;; Mbps

(s/def ::virtualNetworkInterfaceRequirements
  (s/keys :opt-un [::nicIoRequirements ::bandwidthRequirements]))

(s/def ::appExtCpd-item
  (s/keys :req-un [::cpdId ::layerProtocol]
          :opt-un [::l3AddressData ::virtualNetworkInterfaceRequirements]))

(s/def ::appExtCpd
  (s/coll-of ::appExtCpd-item :kind vector?))


;;
;; MEC Service Requirements
;;

(s/def ::serName
  #{:rnis :location :ue-identity :bandwidth-management :wlan-information
    :fixed-access-information :traffic-management})

(s/def ::serCategory
  (s/and string? #(re-matches #"^[a-z0-9\-]+$" %)))

(s/def ::version
  (s/and string? #(re-matches #"^\d+\.\d+\.\d+$" %)))

(s/def ::transportDependencyType
  #{:REST :WEBSOCKET :MQTT :AMQP})

(s/def ::serializer
  #{:JSON :XML :PROTOBUF})

(s/def ::labels
  (s/map-of keyword? string?))

(s/def ::transportDependency
  (s/keys :req-un [::transportDependencyType]
          :opt-un [::serializer ::labels]))

(s/def ::requestedPermissions
  (s/coll-of keyword? :kind vector? :min-count 1))

(s/def ::appServiceRequired-item
  (s/keys :req-un [::serName]
          :opt-un [::serCategory ::version ::transportDependency
                   ::requestedPermissions]))

(s/def ::appServiceRequired
  (s/coll-of ::appServiceRequired-item :kind vector?))


;;
;; Traffic Rule Descriptor
;;

(s/def ::trafficRuleId
  (s/and string? #(re-matches #"^[a-z0-9\-]+$" %)))

(s/def ::filterType
  #{:FLOW :PACKET :HTTP})

(s/def ::priority
  (s/and int? #(<= 0 % 255)))

(s/def ::srcAddress
  (s/coll-of string? :kind vector?))

(s/def ::dstAddress
  (s/coll-of string? :kind vector?))

(s/def ::srcPort
  (s/coll-of string? :kind vector?))

(s/def ::dstPort
  (s/coll-of string? :kind vector?))

(s/def ::protocol
  (s/coll-of string? :kind vector?))

(s/def ::trafficFilter
  (s/keys :req-un [::filterType]
          :opt-un [::srcAddress ::dstAddress ::srcPort ::dstPort ::protocol]))

(s/def ::action
  #{:DROP :FORWARD :PASSTHROUGH :DUPLICATE})

(s/def ::interfaceType
  #{:TUNNEL :MAC :IP})

(s/def ::state
  #{:ACTIVE :INACTIVE})

(s/def ::dstInterface
  (s/coll-of (s/keys :req-un [::interfaceType]) :kind vector?))

(s/def ::trafficRuleDescriptor-item
  (s/keys :req-un [::trafficRuleId ::filterType ::priority ::trafficFilter ::action]
          :opt-un [::dstInterface ::state]))

(s/def ::trafficRuleDescriptor
  (s/coll-of ::trafficRuleDescriptor-item :kind vector?))


;;
;; DNS Rule Descriptor
;;

(s/def ::dnsRuleId
  (s/and string? #(re-matches #"^[a-z0-9\-]+$" %)))

(s/def ::domainName
  (s/and string? #(re-matches #"^[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]{0,61}[a-zA-Z0-9])?)*$" %)))

(s/def ::ipAddressType
  #{:IPV4 :IPV6})

(s/def ::ipAddress
  (s/and string? #(or (re-matches #"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$" %)
                      (re-matches #"^([0-9a-fA-F]{0,4}:){7}[0-9a-fA-F]{0,4}$" %))))

(s/def ::ttl pos-int?)

(s/def ::dnsRuleDescriptor-item
  (s/keys :req-un [::dnsRuleId ::domainName ::ipAddressType ::ipAddress]
          :opt-un [::ttl]))

(s/def ::dnsRuleDescriptor
  (s/coll-of ::dnsRuleDescriptor-item :kind vector?))


;;
;; Feature Dependencies
;;

(s/def ::featureName
  (s/and string? #(<= 1 (count %) 100)))

(s/def ::featureVersion
  (s/and string? #(re-matches #"^\d+\.\d+(\.\d+)?$" %)))

(s/def ::appFeatureRequired-item
  (s/keys :req-un [::featureName ::featureVersion]))

(s/def ::appFeatureRequired
  (s/coll-of ::appFeatureRequired-item :kind vector?))


;;
;; Latency Requirements
;;

(s/def ::maxLatency pos-int?) ;; in milliseconds

(s/def ::latencyDescriptor
  (s/keys :req-un [::maxLatency]))


;;
;; Operation Configuration (optional, simplified)
;;

(s/def ::terminateAppInstanceOpConfig
  map?)

(s/def ::changeAppInstanceStateOpConfig
  map?)

(s/def ::userDefinedData
  map?)


;;
;; Complete MEC AppD Content Schema
;;

(s/def ::mec-appd-content
  (s/keys :req-un [::appDId
                   ::appDVersion
                   ::appName
                   ::appProvider
                   ::appSoftVersion
                   ::mecVersion
                   ::virtualComputeDescriptor
                   ::swImageDescriptor]
          :opt-un [::appInfoName
                   ::appDescription
                   ::virtualStorageDescriptor
                   ::appExtCpd
                   ::appServiceRequired
                   ::appFeatureRequired
                   ::userDefinedData
                   ::trafficRuleDescriptor
                   ::dnsRuleDescriptor
                   ::latencyDescriptor
                   ::terminateAppInstanceOpConfig
                   ::changeAppInstanceStateOpConfig]))


;;
;; Module Subtype Schema
;;

(def subtype "application_mec")

(s/def ::subtype #{subtype})

(s/def ::content ::mec-appd-content)

(def module-application-mec-keys-spec
  (su/merge-keys-specs
    [common/common-attrs
     {:req-un [::appDId
               ::appDVersion
               ::appName
               ::appProvider
               ::appSoftVersion
               ::mecVersion
               ::virtualComputeDescriptor
               ::swImageDescriptor]
      :opt-un [::appInfoName
               ::appDescription
               ::virtualStorageDescriptor
               ::appExtCpd
               ::appServiceRequired
               ::appFeatureRequired
               ::userDefinedData
               ::trafficRuleDescriptor
               ::dnsRuleDescriptor
               ::latencyDescriptor
               ::terminateAppInstanceOpConfig
               ::changeAppInstanceStateOpConfig]}]))

(def module-application-mec-schema (su/only-keys-maps module-application-mec-keys-spec))

(s/def ::schema module-application-mec-schema)


;;
;; Validation Helpers
;;

(defn valid-mec-appd?
  "Validates a MEC AppD content structure"
  [appd-content]
  (s/valid? ::mec-appd-content appd-content))

(defn explain-mec-appd
  "Explains validation errors for MEC AppD content"
  [appd-content]
  (s/explain ::mec-appd-content appd-content))

(defn mec-appd-problems
  "Returns validation problems for MEC AppD content"
  [appd-content]
  (s/explain-data ::mec-appd-content appd-content))
