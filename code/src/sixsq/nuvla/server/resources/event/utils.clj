(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [clj-time.core :as time]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event :as event]
    [sixsq.nuvla.server.resources.spec.event :as event-spec]))


(defn create-event
  [resource-href message acl & {:keys [severity type]
                                :or   {severity event-spec/severity-medium
                                       type     event-spec/type-action}}]

  (let [event-map {:resource-type event/resource-type
                   :content       {:resource {:href resource-href}
                                   :state    message}
                   :severity      severity
                   :type          type
                   :timestamp     (u/unparse-timestamp-datetime (time/now))
                   :acl           acl}
        create-request {:params      {:resource-name event/resource-type}
                        :body        event-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))
