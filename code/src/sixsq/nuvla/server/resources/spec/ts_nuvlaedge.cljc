(ns sixsq.nuvla.server.resources.spec.ts-nuvlaedge
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [sixsq.nuvla.server.util.time :as time]
    [spec-tools.core :as st]))

(s/def ::nuvlaedge-id
  (-> (st/spec string?)
      (assoc :name "nuvlaedge-id"
             :json-schema/field-type :dimension
             :json-schema/type "string"
             :json-schema/description "identifier of nuvlaedge")))

(def metrics #{"online-status" "cpu" "ram" "disk" "network" "power-consumption"})

(s/def ::metric
  (-> (st/spec metrics)
      (assoc :name "metric"
             :json-schema/field-type :dimension
             :json-schema/type "string"
             :json-schema/description "metric"
             :json-schema/value-scope {:values (vec metrics)})))

(s/def ::timestamp
  (-> (st/spec time/date-from-str)
      (assoc :name "@timestamp"
             :json-schema/field-type :timestamp
             :json-schema/description "UTC timestamp"
             :json-schema/type "date-time")))

(s/def ::online
  (assoc (st/spec #{0 1})
    :name "online"
    :json-schema/field-type :metric-gauge
    :json-schema/type "integer"
    :json-schema/description "online/offline"))

(s/def ::online-status
  (assoc (st/spec (su/only-keys :req-un [::online]))
    :name "online-status"
    :json-schema/type "map"
    :json-schema/display-name "Online status"
    :json-schema/description "Online/offline"))

(s/def ::capacity
  (assoc (st/spec pos-int?)
    :name "capacity"
    :json-schema/field-type :metric-gauge
    :json-schema/type "integer"
    :json-schema/description "total capacity of the resource"))

(s/def ::load
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "load"
    :json-schema/field-type :metric-gauge
    :json-schema/type "double"
    :json-schema/description "CPU load"))

(s/def ::load-1
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "load-1"
    :json-schema/field-type :metric-gauge
    :json-schema/type "double"
    :json-schema/description "CPU load last minute"))

(s/def ::load-5
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "load-5"
    :json-schema/field-type :metric-gauge
    :json-schema/type "double"
    :json-schema/description "CPU load last 5 minutes"))

(s/def ::context-switches
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "context-switches"
    :json-schema/field-type :metric-counter
    :json-schema/type "double"
    :json-schema/description "number of CPU context switches since boot"))

(s/def ::interrupts
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "interrupts"
    :json-schema/field-type :metric-counter
    :json-schema/type "double"
    :json-schema/description "number of CPU interrupts since boot"))

(s/def ::software-interrupts
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "software-interrupts"
    :json-schema/field-type :metric-counter
    :json-schema/type "double"
    :json-schema/description "number of CPU software interrupts since boot"))

(s/def ::system-calls
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "system-calls"
    :json-schema/field-type :metric-counter
    :json-schema/type "double"
    :json-schema/description "number of CPU system calls since boot"))

(s/def ::cpu
  (assoc (st/spec (su/only-keys
                    :req-un [::capacity
                             ::load]
                    :opt-un [::load-1
                             ::load-5
                             ::context-switches
                             ::interrupts
                             ::software-interrupts
                             ::system-calls]))
    :name "cpu"
    :json-schema/type "map"
    :json-schema/display-name "CPU"
    :json-schema/description "CPU capacity and current load"))

(s/def ::used
  (assoc (st/spec nat-int?)
    :name "used"
    :json-schema/field-type :metric-gauge
    :json-schema/type "integer"
    :json-schema/description "used quantity of the resource"))

(s/def ::ram
  (assoc (st/spec (su/only-keys
                    :req-un [::capacity
                             ::used]))
    :name "ram"
    :json-schema/type "map"
    :json-schema/description "available and consumed RAM"))

(s/def ::device
  (assoc (st/spec ::core/nonblank-string)
    :name "device"
    :json-schema/field-type :dimension
    :json-schema/description "name of disk device"))

(s/def ::disk
  (assoc (st/spec (su/only-keys
                    :req-un [::device
                             ::capacity
                             ::used]))
    :name "disk-info"
    :json-schema/type "map"
    :json-schema/description "available and consumed disk space for device"))

(s/def ::bytes-transmitted
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "bytes-transmitted"
    :json-schema/field-type :metric-counter
    :json-schema/type "double"
    :json-schema/description "number of bytes transmitted tx_bytes"))

(s/def ::bytes-received
  (assoc (st/spec (s/and number? #(not (neg? %))))
    :name "bytes-received"
    :json-schema/field-type :metric-counter
    :json-schema/type "double"
    :json-schema/description "number of bytes received rx_bytes"))

(s/def ::interface
  (assoc (st/spec ::core/nonblank-string)
    :name "interface"
    :json-schema/field-type :dimension
    :json-schema/description "network interface name"))

(s/def ::network
  (assoc (st/spec (su/only-keys
                    :req-un [::interface
                             ::bytes-received
                             ::bytes-transmitted]))
    :name "net-interface-stat"
    :json-schema/type "map"
    :json-schema/description "txBytes and rxBytes for each network interface"))

(s/def ::metric-name
  (assoc (st/spec ::core/nonblank-string)
    :name "metric-name"
    :json-schema/field-type :dimension
    :json-schema/description "name of the metric"))

(s/def ::energy-consumption
  (assoc (st/spec number?)
    :name "energy-consumption"
    :json-schema/field-type :metric-gauge
    :json-schema/description "value of energy consumption for the metric"))

(s/def ::unit
  (assoc (st/spec ::core/nonblank-string)
    :name "unit"
    :json-schema/description "metric value units"))

(s/def ::power-consumption
  (assoc (st/spec (su/only-keys
                    :req-un [::metric-name
                             ::energy-consumption
                             ::unit]))
    :name "power-consumption-metric"
    :json-schema/type "map"
    :json-schema/description "{metric-name energy-consumption unit} for a specifc power consumption metric"))

(def ts-nuvlaedge-keys-spec {:req-un [::nuvlaedge-id
                                      ::metric
                                      ::timestamp]
                             :opt-un [::online-status
                                      ::cpu
                                      ::ram
                                      ::disk
                                      ::network
                                      ::power-consumption]})


(s/def ::schema
  (assoc (st/spec (su/only-keys-maps ts-nuvlaedge-keys-spec))
    :time-series-routing-path ["nuvlaedge-id"
                               "metric"
                               "disk.device"
                               "network.interface"
                               "power-consumption.metric-name"]))
