(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.event :as event]
    [sixsq.nuvla.server.util.time :as time]))


(defn ^{:deprecated    true
        :superseded-by "sixsq.nuvla.server.resources.common/create-event"}
  create-event-old
  [resource-href state acl & {:keys [severity category timestamp]
                              :or   {severity "medium"
                                     category "action"}}]
  (let [event-map      {:resource-type event/resource-type
                        :content       {:resource {:href resource-href}
                                        :state    state}
                        :severity      severity
                        :category      category
                        :timestamp     (or timestamp (time/now-str))
                        :acl           acl}
        create-request {:params      {:resource-name event/resource-type}
                        :body        event-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))


(defn search-event
  ([resource-href opts]
   (search-event (merge opts {:resource-href resource-href})))
  ([{:keys [event-type resource-href category start end]}]
   (some-> event/resource-type
           (crud/query-as-admin
             {:cimi-params
              {:filter (parser/parse-cimi-filter
                         (str/join " and "
                                   (cond-> []
                                           resource-href (conj (str "resource/href='" resource-href "'"))
                                           event-type (conj (str "event-type='" event-type "'"))
                                           category (conj (str "category='" category "'"))
                                           start (conj (str "timestamp>='" start "'"))
                                           end (conj (str "timestamp<'" end "'")))))}})
           second)))
