(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-1
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::identifier
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "identifier"
             :json-schema/description "host-level unique identifer for peripheral"

             :json-schema/order 31)))


(s/def ::available
  (-> (st/spec boolean?)
      (assoc :name "available"
             :json-schema/type "boolean"
             :json-schema/description "flag to indicate availability of peripheral"

             :json-schema/order 32)))


(s/def ::device-path
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "device-path"
             :json-schema/display-name "device path"
             :json-schema/description "host-level device path"

             :json-schema/order 33)))


(s/def ::interface
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "interface"
             :json-schema/description "hardware interface used to access the peripheral"

             :json-schema/order 34)))


(s/def ::vendor
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vendor"
             :json-schema/description "name of the vendor"

             :json-schema/order 35)))


(s/def ::product
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "product"
             :json-schema/description "description of the product"

             :json-schema/order 36)))


(s/def ::class
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "class"
             :json-schema/description "class of the peripheral"

             :json-schema/order 37)))


(s/def ::classes
  (-> (st/spec (s/coll-of ::class :kind vector?))
      (assoc :name "classes"
             :json-schema/description "all classes of the peripheral"

             :json-schema/order 38)))


(s/def ::port
  (-> (st/spec ::core/port)
    (assoc :name "port"
           :json-schema/description "port number being used by the peripheral"

           :json-schema/order 39)))

;;
;; data gateway attrs
;;

(s/def ::local-data-gateway-endpoint
  (-> (st/spec ::core/url)
    (assoc :name "local-data-gateway-endpoint"
           :json-schema/description "nuvlabox internal data gateway endpoint to access this peripheral data"

           :json-schema/order 40)))


(s/def ::raw-data-sample
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "raw-data-sample"
           :json-schema/description "raw random data sample collected from the peripheral"

           :json-schema/order 41)))


(s/def ::data-gateway-enabled
  (-> (st/spec boolean?)
    (assoc :name "data-gateway-enabled"
           :json-schema/type "boolean"
           :json-schema/description "identifies whether the data gateway is enabled for this peripheral or not"

           :json-schema/order 42)))


(s/def ::serial-number
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "serial-number"
           :json-schema/description "serial number of the peripheral device"

           :json-schema/order 43)))


(s/def ::video-device
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "video-device"
           :json-schema/description "path to video device in the filesystem"

           :json-schema/order 44)))


(s/def ::devices
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
    (assoc :name "additional-devices"
           :json-schema/type "array"
           :json-schema/description "additional devices in the filesystem, related with the peripheral"

           :json-schema/order 45)))


(s/def ::libraries
  (-> (st/spec (s/coll-of ::core/nonblank-string :min-count 1 :kind vector?))
    (assoc :name "libraries"
           :json-schema/type "array"
           :json-schema/description "additional libraries related with the peripheral"

           :json-schema/order 46)))


(s/def ::additional-assets
  (-> (st/spec (su/only-keys :opt-un [::devices ::libraries]))
    (assoc :name "additional-assets"
           :json-schema/type "map"
           :json-schema/description "additional assets that might be related with the peripheral"

           :json-schema/order 47)))


(s/def ::unit
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "unit"
           :json-schema/description "unit name for the specified resource (e.g. 'cuda cores', or 'memory'...)"

           :json-schema/order 48)))


(s/def ::capacity
  (-> (st/spec ::core/nonblank-string)
    (assoc :name "capacity"
           :json-schema/description "total capacity of the specified resource (usually a number, but string are also accepted)"

           :json-schema/order 49)))


(s/def ::load
  (-> (st/spec (s/and number? #(not (neg? %))))
    (assoc :name "load"
           :json-schema/description "current load (as a percentage) of the specified resource"

           :json-schema/order 50)))


(s/def ::resource
  (-> (st/spec (su/only-keys :req-un [::unit ::capacity] :opt-un [::load]))
    (assoc :name "resource"
           :json-schema/type "map"
           :json-schema/description "single resource structure, with unit, capacity and optional load"

           :json-schema/order 51)))


(s/def ::resources
  (-> (st/spec (s/coll-of ::resource :kind vector?))
    (assoc :name "resources"
           :json-schema/type "array"
           :json-schema/description "list of resources"

           :json-schema/order 52)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::identifier
                               ::available
                               ::classes]
                      :opt-un [::device-path
                               ::port
                               ::additional-assets
                               ::interface
                               ::vendor
                               ::product
                               ::local-data-gateway-endpoint
                               ::raw-data-sample
                               ::data-gateway-enabled
                               ::serial-number
                               ::video-device
                               ::resources]}))
