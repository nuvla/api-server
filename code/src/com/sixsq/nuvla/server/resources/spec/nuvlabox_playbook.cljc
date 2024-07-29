(ns com.sixsq.nuvla.server.resources.spec.nuvlabox-playbook
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::enabled
  (-> (st/spec boolean?)
      (assoc :name "enabled"
             :json-schema/type "boolean"
             :json-schema/description "flag to indicate if the playbook is enabled"

             :json-schema/order 31)))


(s/def ::run
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "run"
             :json-schema/description "script to be executed by the NuvlaBox device"

             :json-schema/order 32)))


(s/def ::type
  (-> (st/spec #{"EMERGENCY" "MANAGEMENT"})
      (assoc :name "type"
             :json-schema/type "string"
             :json-schema/description "indicates what type of playbook this is"
             :json-schema/value-scope {:values ["EMERGENCY" "MANAGEMENT"]}

             :json-schema/order 33)))


(s/def ::output
  (-> (st/spec string?)
      (assoc :name "output"
             :json-schema/description "truncated output from previous executions of this playbook"

             :json-schema/order 33)))


(s/def ::schema
  (su/only-keys-maps common/common-attrs
                     {:req-un [::common/parent
                               ::run
                               ::type
                               ::enabled]
                      :opt-un [::output]}))
