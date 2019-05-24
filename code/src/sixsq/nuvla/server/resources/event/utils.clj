(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.event :as event]
    [sixsq.nuvla.server.util.time :as time]))


(defn create-event
  [resource-href message acl & {:keys [severity category]
                                :or   {severity "medium"
                                       category "action"}}]

  (let [event-map      {:resource-type event/resource-type
                        :content       {:resource {:href resource-href}
                                        :state    message}
                        :severity      severity
                        :category      category
                        :timestamp     (time/now-str)
                        :acl           acl}
        create-request {:params      {:resource-name event/resource-type}
                        :body        event-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))
