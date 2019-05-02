(ns sixsq.nuvla.server.resources.spec.nuvlabox-identifier
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::identifier
  (-> (st/spec string?)
      (assoc :name "identifier"
             :json-schema/name "identifier"
             :json-schema/type "string"

             :json-schema/description "identifier to associate with a NuvlaBox"
             :json-schema/order 10)))


(s/def ::series
  (-> (st/spec string?)
      (assoc :name "series"
             :json-schema/name "series"
             :json-schema/type "string"

             :json-schema/displayName "series"
             :json-schema/description "name of the series of NuvlaBox identifiers"
             :json-schema/order 11)))


(s/def ::nuvlabox
  (-> (st/spec ::c/resource-link)
      (assoc :name "nuvlabox"
             :json-schema/name "nuvlabox"
             :json-schema/type "map"

             :json-schema/displayName "NuvlaBox"
             :json-schema/description "id of NuvlaBoxRecord resource"
             :json-schema/order 12)))


(s/def ::nuvlabox-identifier
  (su/only-keys-maps c/common-attrs
                     {:req-un [::identifier
                               ::series]
                      :opt-un [::nuvlabox]}))
