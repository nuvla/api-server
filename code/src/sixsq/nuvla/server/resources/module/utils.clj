(ns sixsq.nuvla.server.resources.module.utils
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
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

(defn module-subtype
  [module]
  (:subtype module))

(defn is-subtype?
  [resource expected]
  (= (module-subtype resource) expected))

(defn is-application?
  [resource]
  (is-subtype? resource subtype-app))

(defn is-application-k8s?
  [resource]
  (is-subtype? resource subtype-app-k8s))

(defn is-applications-sets?
  [resource]
  (is-subtype? resource subtype-apps-sets))

(defn is-component?
  [resource]
  (is-subtype? resource subtype-comp))

(defn is-project?
  [resource]
  (is-subtype? resource subtype-project))

(def is-not-project?
  (complement is-project?))

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
  (if (is-project? resource)
    resource
    (assoc resource :published (boolean (some :published versions)))))

(defn throw-cannot-publish-project
  [resource]
  (if (is-project? resource)
    (throw (r/ex-response "project cannot be published" 400))
    resource))

(defn last-index
  [versions]
  (loop [i (dec (count versions))]
    (when (not (neg? i))
      (if (some? (nth versions i))
        i
        (recur (dec i))))))

(defn split-uuid
  [full-uuid]
  (let [[uuid-module index] (str/split full-uuid #"_")
        index (some-> index edn/read-string)]
    [uuid-module index]))

(defn full-uuid->uuid
  [full-uuid]
  (-> full-uuid split-uuid first))

(defn full-uuid->version-index
  [full-uuid]
  (-> full-uuid split-uuid second))

(defn latest-or-version-index
  [{:keys [versions] :as _module-meta} full-uuid]
  (or (full-uuid->version-index full-uuid)
      (last-index versions)))

(defn get-content-id
  [{:keys [versions] :as _module-meta} version-index]
  (-> versions (nth version-index) :href))

(defn retrieve-module-meta
  [{{full-uuid     :uuid
     resource-name :resource-name} :params :as request}]
  (-> (str resource-name "/" (full-uuid->uuid full-uuid))
      (db/retrieve request)
      (a/throw-cannot-view request)))

(defn resolve-content
  [{:keys [id] :as module-meta}
   {{full-uuid :uuid} :params :as _request}]
  (let [version-index (latest-or-version-index module-meta full-uuid)]
    (if-let [content-id (get-content-id module-meta version-index)]
      (-> content-id
          crud/retrieve-by-id-as-admin
          (dissoc :resource-type :operations :acl))
      (throw (r/ex-not-found (str "Module version not found: " id))))))

(defn retrieve-module-content
  [module-meta request]
  (if (is-project? module-meta)
    module-meta
    (assoc module-meta :content (resolve-content module-meta request))))

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

(defn price-changed?
  [previous-price new-price]
  (not= previous-price new-price))

(defn set-price
  [{new-price :price name :name path :path :as resource}
   previous-price
   active-claim]
  (if (and new-price (price-changed? previous-price new-price))
    (let [{:keys [price-id cent-amount-daily
                  currency follow-customer-trial]}
          (merge previous-price
                 (select-keys new-price [:cent-amount-daily :currency :follow-customer-trial]))
          product-id (some-> price-id pricing-impl/retrieve-price
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
      (assoc resource :price price))
    resource))


(defn can-deploy?
  [resource request]
  (and (a/can-manage? resource request)
       (is-applications-sets? resource)))

(defn resolve-module
  [href request]
  (let [authn-info (auth/current-authentication request)
        module     (crud/get-resource-throw-nok href request)]
    (-> module
        (dissoc :versions :operations)
        (std-crud/resolve-hrefs authn-info true)
        (assoc :versions (:versions module) :href href))))

(defn resolve-from-module
  [request]
  (let [href (get-in request [:body :module :href])]
    (if href
      {:module (resolve-module href request)}
      (logu/log-and-throw-400 "Request body is missing a module href!"))))

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
  [{:keys [id] :as module} {{full-uuid :uuid} :params :as _request}]
  {:application       id
   :version           (latest-or-version-index module full-uuid)
   :applications-sets (get-in module [:content :applications-sets])})


(defn get-applications-sets
  [applications-sets]
  (get-in applications-sets [:content :applications-sets] []))
