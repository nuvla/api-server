(ns sixsq.nuvla.server.resources.spec.data-record
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.data :as data]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(def ^:const infrastructure-service-id-regex #"^infrastructure-service/[a-z0-9]+(-[a-z0-9]+)*(_\d+)?$")


(s/def ::infrastructure-service-id
  (-> (st/spec (s/and string? #(re-matches infrastructure-service-id-regex %)))
      (assoc :name "infrastructure-service-id"
             :json-schema/type "string"

             :json-schema/description "id of service associated with this credential")))


(s/def ::infrastructure-service
  (-> (st/spec ::infrastructure-service-id)
      (assoc :name "infrastructure-service"
             :json-schema/type "string"

             :json-schema/display-name "infrastructure service"
             :json-schema/description "reference to infrastructure-service resource"
             :json-schema/order 21)))


(s/def ::type
  (-> (st/spec #{"volume" "bind" "tmpfs"})
      (assoc :name "target"
             :json-schema/type "string"
             :json-schema/description "target directory in container for mount")))


(s/def ::target
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "target"
             :json-schema/description "target directory in container for mount")))


(s/def ::options
  (-> (st/spec (s/map-of keyword? string?))
      (assoc :name "options"
             :json-schema/type "map"
             :json-schema/description "options to mount data on container"

             :json-schema/indexed false)))


(s/def ::mount
  (-> (st/spec (su/only-keys :req-un [::type ::target ::options]))
      (assoc :name "options"
             :json-schema/type "map"
             :json-schema/description "options to mount data on container")))



(s/def ::schema
  (su/constrained-map keyword? any?
                      common/common-attrs
                      {:req-un [::infrastructure-service]
                       :opt-un [::data/location
                                ::data/timestamp
                                ::data/bytes
                                ::data/content-type
                                ::data/md5sum
                                ::mount]}))
