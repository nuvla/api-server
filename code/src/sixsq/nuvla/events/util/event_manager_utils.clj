(ns sixsq.nuvla.events.util.event-manager-utils
  (:require [sixsq.nuvla.auth.utils :as auth]
            [sixsq.nuvla.events.config :as config]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.event :as event]
            [sixsq.nuvla.server.util.time :as time]))

(def ^:dynamic *parent-event* nil)


(defmacro with-parent-event
  "Sets the given event as the parent event on any additional event created in scope."
  [event-id & body]
  `(binding [*parent-event* ~event-id]
     ~@body))


(defn enrich-event
  "Enriches the given event with additional info."
  [{{:keys [event-type resource timestamp severity acl parent] :as body} :body :as request}]
  (let [active-claim  (auth/current-active-claim request)
        user-id       (auth/current-user-id request)
        session-id    (auth/current-session-id request)
        acl           (or acl (cond-> {:owners ["group/nuvla-admin"]}
                                      active-claim (assoc :view-data [active-claim]
                                                          :view-meta [active-claim])))
        parent        (or parent *parent-event*)
        resource-type (or (some-> resource :resource-type)
                          (some-> resource :href u/id->resource-type))
        {:keys [category default-severity]} (config/get-event-config resource-type event-type)
        event         (cond-> (assoc body
                                :resource-type event/resource-type
                                :severity severity
                                :active-claim (auth/current-active-claim request)
                                :category category
                                :severity (or severity default-severity "medium"))
                              resource-type (assoc-in [:resource :resource-type] resource-type)
                              parent (assoc :parent parent)
                              acl (assoc :acl acl)
                              user-id (assoc :user-id user-id)
                              session-id (assoc :session-id session-id)
                              (nil? timestamp) (assoc :timestamp (time/now-str)))]
    (-> request
        (assoc :body event))))
