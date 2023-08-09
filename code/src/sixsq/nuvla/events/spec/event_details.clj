(ns sixsq.nuvla.events.spec.event-details
  (:require [clojure.spec.alpha :as s]
            [sixsq.nuvla.server.util.spec :as su]
            [spec-tools.core :as st]))


(s/def ::new-state
  (-> (st/spec string?)
      (assoc :name "new state"
             :json-schema/type "string"
             :json-schema/description "new state of the resource")))


(s/def ::old-state
  (-> (st/spec string?)
      (assoc :name "old state"
             :json-schema/type "string"
             :json-schema/description "old state of the resource")))


(s/def ::state-event-details-schema
  (st/spec (su/only-keys-maps {:req-un [::new-state]
                               :opt-un [::old-state]})))

