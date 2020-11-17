(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.event :as event]
    [sixsq.nuvla.server.util.time :as time]
    [sixsq.nuvla.server.util.kafka :as ka]))

(def topic "events")

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
                        :nuvla/authn auth/internal-identity}
        ret             (crud/add create-request)]
    (try
        (log/info (format "Publishing async event: %s %s %s" topic category event-map))
        (ka/publish-async topic category event-map)
        (log/info (format "Published async event: %s %s %s" topic category event-map))
      (catch Exception e
        (log/error (format "Failed publishing to %s topic with key %s: %s" topic category e)))
        (finally
          ret))))
