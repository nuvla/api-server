(ns com.sixsq.nuvla.server.resources.spec.ts-nuvlaedge-availability
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.util.spec :as su]
    [com.sixsq.nuvla.server.util.time :as time]
    [spec-tools.core :as st]))

(s/def ::nuvlaedge-id
  (-> (st/spec string?)
      (assoc :name "nuvlaedge-id"
             :json-schema/field-type :dimension
             :json-schema/type "string"
             :json-schema/description "identifier of nuvlaedge")))

(s/def ::timestamp
  (-> (st/spec time/parse-date)
      (assoc :name "@timestamp"
             :json-schema/field-type :timestamp
             :json-schema/description "UTC timestamp"
             :json-schema/type "date-time")))

(s/def ::online
  (assoc (st/spec #{0 1})
    :name "online"
    :json-schema/field-type :metric-gauge
    :json-schema/type "integer"
    :json-schema/description "offline/online"))

(def ts-nuvlaedge-availability-keys-spec {:req-un [::nuvlaedge-id
                                                   ::timestamp
                                                   ::online]})

(s/def ::schema
  (assoc (st/spec (su/only-keys-maps ts-nuvlaedge-availability-keys-spec))
    :time-series-routing-path ["nuvlaedge-id"]))

