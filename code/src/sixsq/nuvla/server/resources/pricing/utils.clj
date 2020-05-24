(ns sixsq.nuvla.server.resources.pricing.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.pricing.stripe :as s]))


(defn is-product-metadata-in-values?
  [product key values]
  (when-let [values-set (some-> values seq set)]
    (-> product
        (s/get-metadata)
        (get key)
        (values-set))))

(def META_KEY_NUVLA "NUVLA")
(def META_NUVLA_PLAN "PLAN")
(def META_NUVLA_PLAN_ITEM "PLAN_ITEM")
(def META_KEY_ORDER "ORDER")
(def META_KEY_REQUIRED_PLAN_ITEM "REQUIRED_PLAN_ITEM")
(def META_KEY_OPTIONAL_PLAN_ITEM "OPTIONAL_PLAN_ITEM")

(defn is-nuvla-product?
  [product]
  (is-product-metadata-in-values?
    product META_KEY_NUVLA #{META_NUVLA_PLAN META_NUVLA_PLAN_ITEM}))

(defn get-nuvla-products
  []
  (->> (s/list-products {"active" true})
       (s/collection-iterator)
       (filter is-nuvla-product?)))

(defn get-nuvla-plan-splited
  []
  (->> (get-nuvla-products)
       (group-by #(get (s/get-metadata %) META_KEY_NUVLA))))


(defn stripe-product-plan->charge
  [stripe-product-plan]
  (let [amount            (s/price->unit-float (s/get-amount stripe-product-plan))
        aggregate-usage   (s/get-aggregate-usage stripe-product-plan)
        tiers-mode        (s/get-tiers-mode stripe-product-plan)
        tiers             (some->> stripe-product-plan
                                   (s/get-tiers)
                                   seq
                                   (map-indexed
                                     (fn [i tier]
                                       {:order  i
                                        :amount (s/price->unit-float (s/get-unit-amount tier))
                                        :up-to  (s/get-up-to tier)})))
        trial-period-days (s/get-trial-period-days stripe-product-plan)]
    (cond-> {:currency       (s/get-currency stripe-product-plan)
             :interval       (s/get-interval stripe-product-plan)
             :usage-type     (s/get-usage-type stripe-product-plan)
             :billing-scheme (s/get-billing-scheme stripe-product-plan)}
            amount (assoc :amount amount)
            aggregate-usage (assoc :aggregate-usage aggregate-usage)
            tiers-mode (assoc :tiers-mode tiers-mode)
            tiers (assoc :tiers tiers)
            trial-period-days (assoc :trial-period-days trial-period-days))))


(defn transform-plan-items
  [plan-item]
  (let [stripe-product-plans (-> (s/list-plans {"active"  true
                                                "product" (s/get-id plan-item)})
                                 s/collection-iterator
                                 seq)
        metadata             (s/get-metadata plan-item)
        required-items       (some-> metadata (get META_KEY_REQUIRED_PLAN_ITEM) (str/split #","))
        optional-items       (some-> metadata (get META_KEY_OPTIONAL_PLAN_ITEM) (str/split #","))
        order                (some-> (get metadata META_KEY_ORDER) (Integer/parseInt))]
    (map
      (fn [stripe-product-plan]
        (let [id (s/get-id stripe-product-plan)]
          (cond-> {:plan-id id
                   :name    (s/get-name plan-item)
                   :charge  (stripe-product-plan->charge stripe-product-plan)}
                  order (assoc :order order)
                  required-items (assoc :required-items required-items)
                  optional-items (assoc :optional-items optional-items))))
      stripe-product-plans)))

(defn build-nuvla-catalogue
  []
  (let [nuvla-plan-splited     (get-nuvla-plan-splited)
        plans                  (get nuvla-plan-splited META_NUVLA_PLAN [])
        plan-items             (get nuvla-plan-splited META_NUVLA_PLAN_ITEM [])
        tranformed-plans       (->> plans
                                    (map transform-plan-items)
                                    flatten)
        transformed-plan-items (->> plan-items
                                    (map transform-plan-items)
                                    flatten)]
    {:plans      tranformed-plans
     :plan-items transformed-plan-items}))