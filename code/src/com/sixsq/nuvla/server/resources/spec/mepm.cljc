(ns com.sixsq.nuvla.server.resources.spec.mepm
  "Schema for MEC Platform Manager (MEPM) resource.
  
  A MEPM represents an external MEC Platform Manager that Nuvla (MEO) 
  communicates with via the selected Mm3-oriented southbound interface.
  MEPMs manage host-level 
  platform operations on MEC hosts."
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as c]
    [com.sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::name
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "name"
             :json-schema/description "human-readable name of the MEPM"
             :json-schema/order 20)))


(s/def ::description
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "description"
             :json-schema/description "description of the MEPM"
             :json-schema/order 21)))


(s/def ::endpoint
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "endpoint"
             :json-schema/description "Mm3 interface endpoint URL (e.g., https://mepm.example.com/mm3)"
             :json-schema/order 22)))


(s/def ::mec-host-id
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "mec-host-id"
             :json-schema/description "optional reference to associated NuvlaBox (MEC host)"
             :json-schema/order 23)))


(s/def ::platforms
  (-> (st/spec (s/coll-of ::cimi-core/nonblank-string :kind vector?))
      (assoc :name "platforms"
             :json-schema/type "array"
             :json-schema/description "supported container platforms (e.g., kubernetes, docker)"
             :json-schema/order 24)))


(s/def ::services
  (-> (st/spec (s/coll-of ::cimi-core/nonblank-string :kind vector?))
      (assoc :name "services"
             :json-schema/type "array"
             :json-schema/description "available MEC platform services (e.g., traffic-rules, dns-rules)"
             :json-schema/order 25)))


(s/def ::api-version
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "api-version"
             :json-schema/description "MEC API version supported (e.g., 3.1.1)"
             :json-schema/order 26)))


(s/def ::capabilities
  (-> (st/spec (su/only-keys :req-un [::platforms]
                             :opt-un [::services ::api-version]))
      (assoc :name "capabilities"
             :json-schema/type "map"
             :json-schema/description "MEPM capabilities and supported features"
             :json-schema/order 27)))


(s/def ::cpu-cores
  (-> (st/spec pos-int?)
      (assoc :name "cpu-cores"
             :json-schema/type "integer"
             :json-schema/description "number of CPU cores available"
             :json-schema/order 28)))


(s/def ::memory-gb
  (-> (st/spec pos-int?)
      (assoc :name "memory-gb"
             :json-schema/type "integer"
             :json-schema/description "memory available in GB"
             :json-schema/order 29)))


(s/def ::storage-gb
  (-> (st/spec pos-int?)
      (assoc :name "storage-gb"
             :json-schema/type "integer"
             :json-schema/description "storage available in GB"
             :json-schema/order 30)))


(s/def ::gpu-count
  (-> (st/spec nat-int?)
      (assoc :name "gpu-count"
             :json-schema/type "integer"
             :json-schema/description "number of GPUs available"
             :json-schema/order 31)))


(s/def ::resources
  (-> (st/spec (su/only-keys :opt-un [::cpu-cores ::memory-gb ::storage-gb ::gpu-count]))
      (assoc :name "resources"
             :json-schema/type "map"
             :json-schema/description "available compute resources managed by MEPM"
             :json-schema/order 32)))


(s/def ::status
  (-> (st/spec #{"ONLINE" "OFFLINE" "DEGRADED" "ERROR"})
      (assoc :name "status"
             :json-schema/type "string"
             :json-schema/description "current status of the MEPM"
             :json-schema/value-scope {:values  ["ONLINE" "OFFLINE" "DEGRADED" "ERROR"]
                                       :default "ONLINE"}
             :json-schema/order 33)))


(s/def ::credential-id
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "credential-id"
             :json-schema/description "reference to credential resource for Mm3 authentication"
             :json-schema/order 34)))


(s/def ::version
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "version"
             :json-schema/description "MEPM software version"
             :json-schema/order 35)))


(s/def ::tags
  (-> (st/spec (s/coll-of ::cimi-core/nonblank-string :kind vector?))
      (assoc :name "tags"
             :json-schema/type "array"
             :json-schema/description "tags for categorization (e.g., production, 5g, edge)"
             :json-schema/order 36)))


(s/def ::last-check
  (-> (st/spec ::cimi-core/timestamp)
      (assoc :name "last-check"
             :json-schema/description "timestamp of last health check"
             :json-schema/order 37)))


(s/def ::mm3-subscription-id
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "mm3-subscription-id"
             :json-schema/description "southbound Mm3.003 lifecycle subscription identifier"
             :json-schema/order 38)))


(s/def ::mm3-subscription-callback-uri
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "mm3-subscription-callback-uri"
             :json-schema/description "callback URI registered with the MEPM for southbound lifecycle notifications"
             :json-schema/order 39)))


(s/def ::mm3-last-notification
  (-> (st/spec map?)
      (assoc :name "mm3-last-notification"
             :json-schema/type "map"
             :json-schema/description "summary of the most recent southbound Mm3.003 lifecycle notification"
             :json-schema/order 40)))


(s/def ::schema
  (su/only-keys-maps c/common-attrs
                     {:req-un [::name ::endpoint ::capabilities ::status]
                      :opt-un [::description
                               ::mec-host-id
                               ::resources
                               ::credential-id
                               ::version
                               ::tags
                               ::last-check
                               ::mm3-subscription-id
                               ::mm3-subscription-callback-uri
                               ::mm3-last-notification]}))
