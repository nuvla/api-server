(ns sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-0
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.spec :as su]
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


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::identifier
                               ::available
                               ::classes]
                      :opt-un [::device-path
                               ::interface
                               ::vendor
                               ::product
                               ]}))
