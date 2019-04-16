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


(s/def ::location
  (-> (st/spec (s/coll-of double? :min-count 2 :max-count 3))
      (assoc :name "location"
             :json-schema/type "geo-point"
             :json-schema/display-name "location"
             :json-schema/description "location [longitude, latitude, altitude] associated with the data"

             :json-schema/order 24)))
