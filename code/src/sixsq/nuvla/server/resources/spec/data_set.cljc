(ns sixsq.nuvla.server.resources.spec.data-set
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.common :as c]
    [sixsq.nuvla.server.resources.spec.core :as cimi-core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::module-filter
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "module-filter"
             :json-schema/name "module-filter"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "module filter"
             :json-schema/description "filter for modules associated with this data set"
             :json-schema/help "filter for modules associated with this data set"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::data-object-filter
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "data-object-filter"
             :json-schema/name "data-object-filter"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "data object filter"
             :json-schema/description "filter for data-object resources associated with this data set"
             :json-schema/help "filter for data-object resources associated with this data set"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::data-record-filter
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "data-record-filter"
             :json-schema/name "data-record-filter"
             :json-schema/type "string"
             :json-schema/provider-mandatory true
             :json-schema/consumer-mandatory true
             :json-schema/mutable true
             :json-schema/consumer-writable true

             :json-schema/display-name "data record filter"
             :json-schema/description "filter for data-record resources associated with this data set"
             :json-schema/help "filter for data-record resources associated with this data set"
             :json-schema/group "body"
             :json-schema/order 20
             :json-schema/hidden false
             :json-schema/sensitive false)))


(s/def ::schema
  (su/only-keys-maps c/common-attrs
                     {:opt-un [::module-filter
                               ::data-object-filter
                               ::data-record-filter]}))
