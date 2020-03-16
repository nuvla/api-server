(ns sixsq.nuvla.server.resources.spec.nuvlabox-peripheral-1
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


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     nb-status/attributes
                     {:req-un [::identifier
                               ::available
                               ::classes]
                      :opt-un [::device-path
                               ::port
                               ::interface
                               ::vendor
                               ::product
                               ::local-data-gateway-endpoint
                               ::raw-data-sample
                               ::data-gateway-enabled
                               ::serial-number
                               ::video-device]}))
