(ns sixsq.nuvla.server.resources.module.utils
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.util.response :as r]))

(def ^:const subtype-comp "component")

(def ^:const subtype-app "application")

(def ^:const subtype-app-k8s "application_kubernetes")

(def ^:const subtype-project "project")


(defn is-application?
  [subtype]
  (= subtype subtype-app))

(defn is-application-k8s?
  [subtype]
  (= subtype subtype-app-k8s))

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
