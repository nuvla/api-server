(ns sixsq.nuvla.server.resources.spec.ts-nuvlaedge
  (:require
    [clojure.spec.alpha :as s]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))

(s/def ::nuvlaedge-id
  (-> (st/spec string?)
      (assoc :name "nuvlaedge-id"
             :json-schema/type "string"
             :json-schema/description "identifier of nuvlaedge")))


(s/def ::mem
  (-> (st/spec number?)
      (assoc :name "used memory"
             :json-schema/type "double"
             :json-schema/description "used memory in MegaBytes (RAM)")))

(s/def ::load
  (-> (st/spec number?)
      (assoc :name "system load"
             :json-schema/type "double"
             :json-schema/description "System load percentage")))

(def module-keys-spec {:req-un [::nuvlaedge-id
                                ::core/timestamp]
                       :opt-un [::load
                                ::mem]})


(s/def ::schema (su/only-keys-maps module-keys-spec))
