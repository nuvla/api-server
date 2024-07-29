(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-cluster
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


;;
;; general information
;;

(s/def ::version
  (-> (st/spec nat-int?)
      (assoc :name "version"
             :json-schema/type "integer"
             :json-schema/description "schema version"

             :json-schema/order 30)))


(def attributes {:req-un [::version]})


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     attributes))
