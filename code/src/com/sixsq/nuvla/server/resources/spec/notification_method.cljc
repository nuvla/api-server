(ns com.sixsq.nuvla.server.resources.spec.notification-method
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::method
  (-> (st/spec #{"email"
                 "slack"
                 "mqtt"})
      (assoc :name "method"
             :json-schema/type "string"
             :json-schema/description "notification method"

             :json-schema/order 30)))


(s/def ::destination
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "destination"
             :json-schema/description "notification destination"

             :json-schema/order 31)))

(s/def ::mqtt-cred
  (assoc cred-spec/credential-id-spec
    :name "mqtt-cred"
    :json-schema/description "Mqtt credential"))

(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::method
                               ::destination]}
                     {:opt-un [::mqtt-cred]}))

