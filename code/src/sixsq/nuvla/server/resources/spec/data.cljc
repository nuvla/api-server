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
  (-> (st/spec (s/coll-of number? :min-count 2 :max-count 3))
      (assoc :name "location"
             :json-schema/type "geo-point"
             :json-schema/display-name "location"
             :json-schema/description "location [longitude, latitude[, altitude]] associated with the data"

             :json-schema/order 24)))


(s/def ::type
  (-> (st/spec #{"Polygon" "MultiPolygon" "Point"})
      (assoc :name "type"
             :json-schema/type "string"
             :json-schema/display-name "type"
             :json-schema/description "Type of the coordinates: Polygon, MultiPolygon, or Point."
             :json-schema/value-scope {:values ["Polygon" "MultiPolygon" "Point"]})))


(s/def ::coordinates
  (-> (st/spec vector?)
      (assoc :name "coordinates"
             :json-schema/type "array"
             :json-schema/display-name "coordinates"
             :json-schema/description "List of closed polygons [[longitude, latitude[, altitude], ...], ...] or :location")))


(s/def ::coordinates-polygon (s/coll-of (s/coll-of ::location :min-count 4)))
(s/def ::coordinates-multi-polygon (s/coll-of ::coordinates-polygon))


(defn valid-coordinates?
  [v]
  (case (:type v)
    "Polygon" (s/valid? ::coordinates-polygon (:coordinates v))
    "MultiPolygon" (s/valid? ::coordinates-multi-polygon (:coordinates v))
    "Point" (s/valid? ::location (:coordinates v))
    false))

(defn closed-polygons?
  [v]
  (every? true? (map #(= (first %) (last %)) v)))
(defn closed-multi-polygons?
  [v]
  (every? true? (map #(closed-polygons? %) v)))
(defn polygons-closed?
  [v]
  (case (:type v)
    "Polygon" (closed-polygons? (:coordinates v))
    "MultiPolygon" (closed-multi-polygons? (:coordinates v))
    true))


(s/def ::geometry
  (-> (st/spec (s/and (su/only-keys :req-un [::type ::coordinates])
                      valid-coordinates?
                      polygons-closed?))
      (assoc :name "geometry"
             :json-schema/type "geo-shape"
             :json-schema/display-name "geometry"
             :json-schema/description "An area associated with data as map of :type and :coordinates. The latter is a list of closed polygons as [[longitude, latitude[, altitude], ...], ...]. The former is \"Polygon\", \"MultiPolygon\", or \"Point\". See https://datatracker.ietf.org/doc/html/rfc7946#section-3.1.6"

             :json-schema/order 25)))
