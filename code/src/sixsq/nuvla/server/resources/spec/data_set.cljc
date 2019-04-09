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
             :json-schema/type "string"

             :json-schema/display-name "module filter"
             :json-schema/description "filter for modules associated with this data set"
             :json-schema/order 20)))


(s/def ::data-object-filter
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "data-object-filter"
             :json-schema/type "string"

             :json-schema/display-name "data object filter"
             :json-schema/description "filter for data-object resources associated with this data set"
             :json-schema/order 20)))


(s/def ::data-record-filter
  (-> (st/spec ::cimi-core/nonblank-string)
      (assoc :name "data-record-filter"
             :json-schema/type "string"

             :json-schema/display-name "data record filter"
             :json-schema/description "filter for data-record resources associated with this data set"
             :json-schema/order 20)))


(s/def ::schema
  (su/only-keys-maps c/common-attrs
                     {:opt-un [::module-filter
                               ::data-object-filter
                               ::data-record-filter]}))
