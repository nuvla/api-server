(ns com.sixsq.nuvla.server.resources.event.utils
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.db.filter.parser :as parser]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.event-config :as ec]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.event :as event]
    [com.sixsq.nuvla.server.util.time :as time]))


(defn request-event-name
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


(defn get-event-name
  [{:keys [event-name] :as context} request]
  (or event-name
      (request-event-name context request)))


(defn get-category
  [{:keys [category] :as _context}]
  (or category "action"))


(defn get-timestamp
  [{:keys [timestamp] :as _context}]
  (or timestamp (time/now-str)))


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
  (cond-> {:href (get-resource-href context response)}
          (contains? context :resource)
          (assoc :content (:resource context))))


(defn get-linked-identifiers
  [{:keys [linked-identifiers] :or {linked-identifiers []} :as _context}]
  linked-identifiers)


(defn get-linked-resource-ids
  [{{:keys [linked-identifiers]} :content :as _event} resource-type]
  (->> linked-identifiers
       (filter (comp #(= resource-type %) u/id->resource-type))))


(defn get-linked-resources
  ([{{:keys [linked-identifiers]} :content :as _event}]
   (->> linked-identifiers
        (keep crud/retrieve-by-id-as-admin1)))
  ([{{:keys [linked-identifiers]} :content :as _event} resource-type]
   (->> linked-identifiers
        (filter (comp #(= resource-type %) u/id->resource-type))
        (keep crud/retrieve-by-id-as-admin1))))


(defn set-description
  [event context]
  (let [event-description (ec/event-description event context)]
    (cond-> event
            event-description (assoc :description event-description))))


(defn build-event
  [context request response]
  (-> {:resource-type event/resource-type
       :name          (get-event-name context request)
       :success       (get-success response)
       :category      (get-category context)
       :timestamp     (get-timestamp context)
       :authn-info    (auth/current-authentication request)
       :acl           (get-acl context response)
       :severity      (get-severity context)
       :content       {:resource           (get-resource context response)
                       :linked-identifiers (get-linked-identifiers context)}}
      (set-description context)))


(defn add-event
  [event]
  (let [create-request {:params      {:resource-name event/resource-type}
                        :body        event
                        :nuvla/authn auth/internal-identity}]
    (crud/add create-request)))


(def topic event/resource-type)


(defn create-event
  [resource-href state acl & {:keys [severity category timestamp]
                              :or   {severity "medium"
                                     category "action"}}]
  (add-event {:name          "legacy"
              :success       true
              :resource-type event/resource-type
              :content       {:resource {:href resource-href}
                              :state    state}
              :severity      severity
              :category      category
              :timestamp     (or timestamp (time/now-str))
              :acl           acl
              :authn-info    {}}))

(defn query-events
  ([resource-href opts]
   (query-events (assoc opts :resource-href resource-href)))
  ([{:keys [resource-href linked-identifier category state start end orderby last] event-name :name :as opts}]
   (some-> event/resource-type
           (crud/query-as-admin
             {:cimi-params
              (cond->
                {:filter (parser/parse-cimi-filter
                           (str/join " and "
                                     (cond-> []
                                             resource-href (conj (str "content/resource/href='" resource-href "'"))
                                             (and (contains? opts :resource-href) (nil? resource-href)) (conj (str "content/resource/href=null"))
                                             event-name (conj (str "name='" event-name "'"))
                                             category (conj (str "category='" category "'"))
                                             state (conj (str "content/state='" state "'"))
                                             linked-identifier (conj (str "content/linked-identifiers='" linked-identifier "'"))
                                             start (conj (str "timestamp>='" start "'"))
                                             end (conj (str "timestamp<'" end "'")))))}
                orderby (assoc :orderby orderby)
                last (assoc :last last))})
           second)))
