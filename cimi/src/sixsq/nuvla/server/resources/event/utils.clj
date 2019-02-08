(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [clj-time.core :as time]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.event :as event]
    [sixsq.nuvla.server.resources.spec.event :as event-spec]))


(defn create-event
  [resource-href message acl & {:keys [severity type]
                                :or   {severity event-spec/severity-medium
                                       type     event-spec/type-action}}]

  (let [event-map {:resource-type event/resource-uri
                   :content       {:resource {:href resource-href}
                                   :state    message}
                   :severity      severity
                   :type          type
                   :timestamp     (u/unparse-timestamp-datetime (time/now))
                   :acl           acl}
        create-request {:params   {:resource-name event/resource-url}
                        :identity std-crud/internal-identity
                        :body     event-map}]
    (crud/add create-request)))
