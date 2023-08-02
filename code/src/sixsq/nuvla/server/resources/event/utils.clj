(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.eventing :as eventing]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event :as event]))


(def topic event/resource-type)


(def create-event eventing/create-event*)



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
