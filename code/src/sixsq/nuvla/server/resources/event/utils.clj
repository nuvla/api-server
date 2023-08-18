(ns sixsq.nuvla.server.resources.event.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event :as event]
    [sixsq.nuvla.server.util.time :as t]
    [sixsq.nuvla.server.util.time :as time]))


(defn request-event-type
  "Returns a string of the form <resource-name>.<action>"
  [{{:keys [resource-name uuid action]} :params :as _context}
   {:keys [request-method] :as _request}]
  (if uuid
    (if action
      (some->> action (str resource-name "."))
      (case request-method
        :put (str resource-name ".edit")
        :delete (str resource-name ".delete")
        nil))
    (case request-method
      :post (str resource-name ".add")
      :delete (str resource-name ".bulk.delete")
      :patch (some->> action (str resource-name ".bulk."))
      nil)))


(defn get-success
  [{:keys [status] :as _response}]
  (<= 200 status 399))


(defn get-event-type
  [{:keys [event-type] :as context} request]
  (or event-type
      (request-event-type context request)))


(defn get-category
  [{:keys [category] :as _context}]
  (or category "action"))


(defn get-timestamp
  [{:keys [timestamp] :as _context}]
  (or timestamp (t/now-str)))


(defn retrieve-by-id
  [id]
  (try
    (:body (crud/retrieve {:params         (u/id->request-params id)
                           :request-method :get
                           :nuvla/authn    auth/internal-identity}))
    (catch Exception _ex
      nil)))


(defn get-resource-href
  [{{:keys [resource-name uuid]} :params :as _context} response]
  (or (some->> uuid (str resource-name "/"))
      (-> response :body :resource-id)))


(defn transform-acl
  [acl]
  (when acl
    {:owners (vec (concat (:edit-data acl) (:owners acl)))}))

(defn derive-acl-from-resource
  [context response]
  (when-let [acl (some-> (get-resource-href context response)
                         retrieve-by-id
                         :acl)]
    (transform-acl acl)))


(defn get-acl
  [{:keys [visible-to acl] :as context} response]
  (let [visible-to (remove nil? visible-to)]
    (or (when (seq visible-to)
          {:owners (-> visible-to (conj "group/nuvla-admin") distinct vec)})
        (transform-acl acl)
        (derive-acl-from-resource context response)
        {:owners ["group/nuvla-admin"]})))


(defn get-severity
  [{:keys [severity] :as _context}]
  (or severity "medium"))


(defn get-resource
  [context response]
  {:href (get-resource-href context response)})


(defn get-linked-identifiers
  [{:keys [linked-identifiers] :or {linked-identifiers []} :as _context}]
  linked-identifiers)


(defn build-event
  [context request response]
  {:resource-type event/resource-type
   :event-type    (get-event-type context request)
   :success       (get-success response)
   :category      (get-category context)
   :timestamp     (get-timestamp context)
   :authn-info    (auth/current-authentication request)
   :acl           (get-acl context response)
   :severity      (get-severity context)
   :content       {:resource           (get-resource context response)
                   :state              "to be removed"
                   :linked-identifiers (get-linked-identifiers context)}})


(defn add-event
  [event]
  (let [create-request {:params      {:resource-name event/resource-type}
                        :body        event
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))


(def topic event/resource-type)


;; FIXME: duplicated
(defn create-event
  [resource-href state acl & {:keys [severity category timestamp]
                              :or   {severity "medium"
                                     category "action"}}]
  (let [event-map      {:event-type    "legacy"
                        :success       true
                        :resource-type event/resource-type
                        :content       {:resource {:href resource-href}
                                        :state    state}
                        :severity      severity
                        :category      category
                        :timestamp     (or timestamp (time/now-str))
                        :acl           acl
                        :authn-info    {}}
        create-request {:params      {:resource-name event/resource-type}
                        :body        event-map
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))


(defn query-events
  ([resource-href opts]
   (query-events (assoc opts :resource-href resource-href)))
  ([{:keys [resource-href event-type linked-identifier category state start end orderby last] :as opts}]
   (some-> event/resource-type
           (crud/query-as-admin
             {:cimi-params
              (cond->
                {:filter (parser/parse-cimi-filter
                           (str/join " and "
                                     (cond-> []
                                             resource-href (conj (str "content/resource/href='" resource-href "'"))
                                             (and (contains? opts :resource-href) (nil? resource-href)) (conj (str "content/resource/href=null"))
                                             event-type (conj (str "event-type='" event-type "'"))
                                             category (conj (str "category='" category "'"))
                                             state (conj (str "content/state='" state "'"))
                                             linked-identifier (conj (str "content/linked-identifiers='" linked-identifier "'"))
                                             start (conj (str "timestamp>='" start "'"))
                                             end (conj (str "timestamp<'" end "'")))))}
                orderby (assoc :orderby orderby)
                last (assoc :last last))})
           second)))

;; FIXME: duplicated
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
