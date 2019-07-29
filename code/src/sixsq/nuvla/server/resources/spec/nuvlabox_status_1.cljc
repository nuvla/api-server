(ns sixsq.nuvla.server.resources.spec.nuvlabox-status-1
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


(s/def ::current-time
  (-> (st/spec ::core/timestamp)
      (assoc :name "current-time"
             :json-schema/display-name "current time"
             :json-schema/description "current time provided by the NuvlaBox clock"

             :json-schema/order 32)))


(s/def ::status
  (-> (st/spec #{"OPERATIONAL" "DEGRADED" "UNKNOWN"})
      (assoc :name "status"
             :json-schema/type "string"
             :json-schema/description "current status of the NuvlaBox"

             :json-schema/order 33)))


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
  (-> (st/spec (s/and number? #(not (neg? %))))
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
                               ::current-time
                               ::comment
                               ::resources
                               ::wifi-password]}))
