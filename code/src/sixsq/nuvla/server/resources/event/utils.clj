(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.event :as event]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.util.time :as time]))


(def topic event/resource-type)


(defn create-event
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
  [resource-href {:keys [category state start end]}]
  (some-> event/resource-type
          (crud/query-as-admin
            {:cimi-params
             {:filter (parser/parse-cimi-filter 
                        (str/join " and " 
                                  (cond-> [(str "content/resource/href='" resource-href "'")]
                                    category (conj (str "category='" category "'"))
                                    state (conj (str "content/state='" state "'"))
                                    start (conj (str "timestamp>='" start "'"))
                                    end (conj (str "timestamp<'" end "'")))))}})
          second))
