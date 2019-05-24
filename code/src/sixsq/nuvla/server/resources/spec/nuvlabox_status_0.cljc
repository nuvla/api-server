(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-0
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;;
;; general information
;;

(s/def ::next-heartbeat
  (-> (st/spec ::core/timestamp)
      (assoc :name "next-heartbeat"
             :json-schema/display-name "next heartbeat"
             :json-schema/description "timestamp for next heartbeat update"

             :json-schema/order 31)))


(s/def ::status
  (-> (st/spec #{"OPERATIONAL" "DEGRADED" "UNKNOWN"})
      (assoc :name "status"
             :json-schema/type "string"
             :json-schema/description "current status of the NuvlaBox"

             :json-schema/order 32)))


(s/def ::comment
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "comment"
             :json-schema/description "comment about the current NuvlaBox status"

             :json-schema/order 10)))


;;
;; resource information
;;

(s/def ::capacity
  (-> (st/spec pos-int?)
      (assoc :name "capacity"
             :json-schema/type "integer"
             :json-schema/description "total capacity of the resource"

             :json-schema/order 11)))


(s/def ::load
  (-> (st/spec (s/and double? #(not (neg? %))))
      (assoc :name "load"
             :json-schema/type "double"
             :json-schema/description "CPU load"

             :json-schema/order 12)))


(s/def ::cpu
  (-> (st/spec (su/only-keys :req-un [::capacity ::load]))
      (assoc :name "cpu"
             :json-schema/type "map"
             :json-schema/display-name "CPU"
             :json-schema/description "CPU capacity and current load"

             :json-schema/order 22)))


(s/def ::used
  (-> (st/spec nat-int?)
      (assoc :name "used"
             :json-schema/type "integer"
             :json-schema/description "used quantity of the resource"

             :json-schema/order 12)))


(s/def ::ram
  (-> (st/spec (su/only-keys :req-un [::capacity ::used]))
      (assoc :name "ram"
             :json-schema/type "map"
             :json-schema/description "available and consumed RAM"

             :json-schema/order 23)))


(s/def ::device
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "device"
             :json-schema/description "name of disk device"

             :json-schema/order 10)))


(s/def ::disk-info
  (-> (st/spec (su/only-keys :req-un [::device ::capacity ::used]))
      (assoc :name "disk-info"
             :json-schema/type "map"
             :json-schema/description "available and consumed disk space for device")))


(s/def ::disks
  (-> (st/spec (s/coll-of ::disk-info :min-count 1 :kind vector?))
      (assoc :name "disks"
             :json-schema/type "array"
             :json-schema/description "available and consumed disk space for devices"

             :json-schema/order 24)))


(s/def ::resources
  (-> (st/spec (su/only-keys :req-un [::cpu ::ram ::disks]))
      (assoc :name "resources"
             :json-schema/type "map"
             :json-schema/description "available and consumed resources"

             :json-schema/order 33)))

;;
;; peripherals
;;

(s/def ::busy
  (-> (st/spec boolean?)
      (assoc :name "busy"
             :json-schema/type "boolean"
             :json-schema/description "flag indicating whether the device is busy"

             :json-schema/order 10)))


(s/def ::vendor-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "vendor-id"
             :json-schema/display-name "vendor ID"
             :json-schema/description "unique identifier of the peripheral vendor"

             :json-schema/order 11)))


(s/def ::device-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "device-id"
             :json-schema/display-name "device ID"
             :json-schema/description "unique identifier of the device"

             :json-schema/order 12)))


(s/def ::bus-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "bus-id"
             :json-schema/display-name "USB bus ID"
             :json-schema/description "unique identifier of the USB bus"

             :json-schema/order 13)))


(s/def ::product-id
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "product-id"
             :json-schema/display-name "product ID"
             :json-schema/description "unique identifier of the product"

             :json-schema/order 14)))


(s/def ::description
  (-> (st/spec string?)
      (assoc :name "description"
             :json-schema/type "string"
             :json-schema/description "description of the peripheral"

             :json-schema/order 14)))


(s/def ::usb-info
  (-> (st/spec (su/only-keys :req-un [::busy
                                      ::vendor-id
                                      ::device-id
                                      ::bus-id
                                      ::product-id
                                      ::description]))
      (assoc :name "usb-info"
             :json-schema/type "map"
             :json-schema/description "USB peripheral information")))


(s/def ::usb
  (-> (st/spec (s/coll-of ::usb-info :kind vector?))
      (assoc :name "usb"
             :json-schema/type "array"
             :json-schema/description "USB peripherals"

             :json-schema/order 20)))


(s/def ::peripherals
  (-> (st/spec (su/only-keys :opt-un [::usb]))
      (assoc :name "peripherals"
             :json-schema/type "map"
             :json-schema/description "state of visible peripherals"

             :json-schema/order 34)))

;;
;; miscellaneous
;;

(s/def ::wifi-password
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "wifi-password"
             :json-schema/description "WIFI password for the NuvlaBox"

             :json-schema/order 35)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::status]
                      :opt-un [::next-heartbeat
                               ::comment
                               ::resources
                               ::peripherals
                               ::wifi-password]}))
