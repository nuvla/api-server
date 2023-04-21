(ns sixsq.nuvla.server.resources.module.utils
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.log :as logu]
    [sixsq.nuvla.server.util.response :as r]))

(def ^:const subtype-comp "component")

(def ^:const subtype-app "application")

(def ^:const subtype-app-k8s "application_kubernetes")

(def ^:const subtype-apps-sets "applications_sets")

(def ^:const subtype-project "project")


(defn is-application?
  [subtype]
  (= subtype subtype-app))

(defn is-application-k8s?
  [subtype]
  (= subtype subtype-app-k8s))

(defn is-applications-sets?
  [subtype]
  (= subtype subtype-apps-sets))

(defn is-component?
  [subtype]
  (= subtype subtype-comp))

(defn is-project?
  [subtype]
  (= subtype subtype-project))


(defn split-resource
  "Splits a module resource into its metadata and content, returning the tuple
   [metadata, content]."
  [{:keys [content] :as body}]
  (let [module-meta (dissoc body :content)]
    [module-meta content]))


(defn get-parent-path
  "Gets the parent path for the given path. The root parent is the empty
   string."
  [path]
  (when path (str/join "/" (-> path (str/split #"/") drop-last))))


(defn set-parent-path
  "Updates the :parent-path key in the module resource to ensure that it is
   consistent with the value of :path."
  [{:keys [path] :as resource}]
  (cond-> resource
          path (assoc :parent-path (get-parent-path path))))


(defn set-published
  "Updates the :parent-path key in the module resource to ensure that it is
   consistent with the value of :path."
  [{:keys [versions] :as resource}]
  (cond-> resource
          (not (is-project? resource)) (assoc :published (boolean (some :published versions)))))


(defn last-index
  [versions]
  (loop [i (dec (count versions))]
    (when (not (neg? i))
      (if (some? (nth versions i))
        i
        (recur (dec i))))))


(defn retrieve-content-id
  [versions index]
  (let [index (or index (last-index versions))]
    (-> versions (nth index) :href)))


(defn split-uuid
  [uuid]
  (let [[uuid-module index] (str/split uuid #"_")
        index (some-> index edn/read-string)]
    [uuid-module index]))


(defn get-module-content
  [{:keys [id versions] :as module-meta} uuid]
  (let [version-index  (second (split-uuid uuid))
        version-id     (retrieve-content-id versions version-index)
        module-content (if version-id
                         (-> version-id
                             (crud/retrieve-by-id-as-admin)
                             (dissoc :resource-type :operations :acl))
                         (when version-index
                           (throw (r/ex-not-found
                                    (str "Module version not found: " id)))))]
    (assoc module-meta :content module-content)))

(defn get-vendor-by-query-as-admin
  [filter-str]
  (let [options {:cimi-params {:filter (parser/parse-cimi-filter filter-str)}}]
    (-> (crud/query-as-admin "vendor" options)
        second
        first)))

(defn active-claim->account-id
  [active-claim]
  (or (some->> active-claim
               (format "parent='%s'")
               get-vendor-by-query-as-admin
               :account-id)
      (throw (r/ex-response (str "unable to resolve vendor account-id for active-claim '"
                                 active-claim "' ") 409))))

(defn resolve-vendor-email
  [{{:keys [account-id]} :price :as module-meta}]
  (if-let [email (some->> account-id
                          (format "account-id='%s'")
                          get-vendor-by-query-as-admin
                          :email)]
    (assoc-in module-meta [:price :vendor-email] email)
    module-meta))

(defn set-price
  [{{:keys [price-id cent-amount-daily currency follow-customer-trial] :as price}
    :price name :name path :path :as body}
   active-claim]
  (if price
    (let [product-id (some-> price-id pricing-impl/retrieve-price
                             pricing-impl/price->map :product-id)
          account-id (active-claim->account-id active-claim)
          s-price    (pricing-impl/create-price
                       (cond-> {"currency"    currency
                                "unit_amount" cent-amount-daily
                                "recurring"   {"interval"        "month"
                                               "aggregate_usage" "sum"
                                               "usage_type"      "metered"}}
                               product-id (assoc "product" product-id)
                               (nil? product-id) (assoc "product_data" {"name"       (or name path)
                                                                        "unit_label" "day"})))
          price      (cond-> {:price-id          (pricing-impl/get-id s-price)
                              :product-id        (pricing-impl/get-product s-price)
                              :account-id        account-id
                              :cent-amount-daily cent-amount-daily
                              :currency          currency}
                             (some? follow-customer-trial) (assoc :follow-customer-trial follow-customer-trial))]
      (assoc body :price price))
    body))


(defn can-deploy?
  [{:keys [subtype] :as resource} request]
  (and (a/can-manage? resource request)
       (is-applications-sets? subtype)))

(defn resolve-module
  [request]
  (let [href                 (get-in request [:body :module :href])
        authn-info           (auth/current-authentication request)
        params               (u/id->request-params href)
        module-request       {:params      params
                              :nuvla/authn authn-info}
        on-error             (fn [_response]
                               (throw (r/ex-bad-request (str "cannot resolve " href))))
        on-success           (fn [{{:keys [versions] :as body} :body}]
                               (-> (dissoc body :versions :operations)
                                   (std-crud/resolve-hrefs authn-info true)
                                   (assoc :versions versions :href href)))
        throw-cannot-resolve (r/configurable-check-response r/status-200? on-success on-error)]
    (-> module-request
        crud/retrieve
        throw-cannot-resolve)))

(defn resolve-from-module
  [request]
  (if (get-in request [:body :module :href])
    (assoc-in request [:body :module] (resolve-module request))
    (logu/log-and-throw-400 "Request body is missing a module href!")))

(defn throw-cannot-deploy
  [resource request]
  (if (can-deploy? resource request)
    resource
    (throw (r/ex-response "operation not available" 400))))

(defn collect-applications-hrefs
  [applications-sets]
  (->> applications-sets
       (mapcat :applications)
       (map #(str (:id %) "_" (:version %)))
       distinct))

(defn resolve-applications-hrefs
  [hrefs request]
  (reduce #(or (some->> {:params         (u/id->request-params %2)
                         :request-method :get
                         :nuvla/authn    (auth/current-authentication request)}
                        crud/retrieve
                        r/ignore-response-not-200
                        :body
                        (assoc %1 %2))
               %1)
          {} hrefs))

(defn update-application-resolved
  [{:keys [id version] :as application} hrefs-map]
  (let [resolved (get hrefs-map (str id "_" version))]
    (cond-> application
            resolved (assoc :resolved resolved))))

(defn update-applications-resolved
  [applications hrefs-map]
  (if-let [applications (seq applications)]
    (map #(update-application-resolved % hrefs-map) applications)
    applications))

(defn update-applications-sets-applications-resolved
  [applications-sets hrefs-map]
  (map #(update % :applications update-applications-resolved hrefs-map) applications-sets))

(defn inject-resolved-applications
  [hrefs-map resource]
  (if-let [applications-sets (some-> resource :applications-sets seq)]
    (assoc resource :applications-sets
                    (update-applications-sets-applications-resolved applications-sets hrefs-map))
    resource))

(defn resolve-referenced-applications
  [resource request]
  (-> resource
      :applications-sets
      collect-applications-hrefs
      (resolve-applications-hrefs request)
      (inject-resolved-applications resource)))

(defn generate-deployment-set-skeleton
  [{:keys [id] :as resource} {{uuid-full :uuid} :params :as _request}]
  (let [version-index (second (split-uuid uuid-full))]
    {:application       id
     :version           (or version-index
                            (last-index (:versions resource)))
     :applications-sets (get-in resource [:content :applications-sets])}))


(defn get-applications-sets
  [applications-sets]
  (get-in applications-sets [:content :applications-sets] []))
