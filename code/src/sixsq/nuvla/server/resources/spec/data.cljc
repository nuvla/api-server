(ns sixsq.nuvla.server.resources.spec.data
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::content-type
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "content-type"
             :json-schema/description "format (mimetype) of the data"

             :json-schema/order 20)))


(s/def ::bytes
  (-> (st/spec nat-int?)
      (assoc :name "bytes"
             :json-schema/type "long"
             :json-schema/description "number of bytes in the data"

             :json-schema/order 21)))


(s/def ::md5sum
  (-> (st/spec ::core/token)
      (assoc :name "md5sum"
             :json-schema/description "MD5 checksum of the data"

             :json-schema/order 22)))


(s/def ::timestamp
  (-> (st/spec ::core/timestamp)
      (assoc :name "timestamp"
             :json-schema/description "timestamp (UTC) associated with the data"

             :json-schema/order 23)))


(s/def ::lon
  (-> (st/spec (s/double-in :min -180.0 :max 180.0 :NaN? false :infinite? false))
      (assoc :name "lon"
             :json-schema/type "double"
             :json-schema/display-name "longitude"
             :json-schema/description "longitude")))


(s/def ::lat
  (-> (st/spec (s/double-in :min -90.0 :max 90.0 :NaN? false :infinite? false))
      (assoc :name "lat"
             :json-schema/type "double"
             :json-schema/display-name "latitude"
             :json-schema/description "latitude")))


(s/def ::alt
  (-> (st/spec (s/double-in :NaN? false :infinite? false))
      (assoc :name "alt"
             :json-schema/type "double"
             :json-schema/display-name "altitude"
             :json-schema/description "altitude")))


(s/def ::location
  (-> (st/spec (su/only-keys :req-un [::lon ::lat]
                             :opt-un [::alt]))
      (assoc :name "location"
             :json-schema/type "geo-point"
             :json-schema/display-name "location"
             :json-schema/description "location (longitude, latitude, altitude) associated with the data"

             :json-schema/order 24)))
