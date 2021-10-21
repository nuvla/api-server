(ns sixsq.nuvla.server.resources.pricing.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.pricing.impl :as pricing-impl]))


(defn is-product-metadata-in-values?
  [product key values]
  (when-let [values-set (some-> values seq set)]
    (-> product
        (pricing-impl/get-metadata)
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
  (->> (pricing-impl/list-products {"active" true})
       (pricing-impl/collection-iterator)
       (filter is-nuvla-product?)))

(defn get-nuvla-plan-splited
  []
  (->> (get-nuvla-products)
       (group-by #(get (pricing-impl/get-metadata %) META_KEY_NUVLA))))


(defn stripe-product-plan->charge
  [stripe-product-plan]
  (let [amount            (pricing-impl/price->unit-float (pricing-impl/get-unit-amount stripe-product-plan))
        recurring         (pricing-impl/get-recurring stripe-product-plan)
        aggregate-usage   (pricing-impl/get-aggregate-usage recurring)
        tiers-mode        (pricing-impl/get-tiers-mode stripe-product-plan)
        tiers             (some->> stripe-product-plan
                                   (pricing-impl/get-tiers)
                                   seq
                                   (map-indexed
                                     (fn [i tier]
                                       {:order  i
                                        :amount (pricing-impl/price->unit-float (pricing-impl/get-unit-amount tier))
                                        :up-to  (pricing-impl/get-up-to tier)})))
        trial-period-days (pricing-impl/get-trial-period-days recurring)]
    (cond-> {:currency       (pricing-impl/get-currency stripe-product-plan)
             :interval       (pricing-impl/get-interval recurring)
             :usage-type     (pricing-impl/get-usage-type recurring)
             :billing-scheme (pricing-impl/get-billing-scheme stripe-product-plan)}
            amount (assoc :amount amount)
            aggregate-usage (assoc :aggregate-usage aggregate-usage)
            tiers-mode (assoc :tiers-mode tiers-mode)
            tiers (assoc :tiers tiers)
            trial-period-days (assoc :trial-period-days trial-period-days))))


(defn transform-plan-items
  [plan-item]
  (let [stripe-product-prices (-> (pricing-impl/list-prices {"active" true
                                                  "product"       (pricing-impl/get-id plan-item)
                                                  "expand"        ["data.tiers"]})
                                  pricing-impl/collection-iterator
                                  seq)
        metadata              (pricing-impl/get-metadata plan-item)
        required-items        (some-> metadata (get META_KEY_REQUIRED_PLAN_ITEM) (str/split #","))
        optional-items        (some-> metadata (get META_KEY_OPTIONAL_PLAN_ITEM) (str/split #","))
        order                 (some-> (get metadata META_KEY_ORDER) (Integer/parseInt))]
    (map
      (fn [stripe-product-plan]
        (let [id (pricing-impl/get-id stripe-product-plan)]
          (cond-> {:plan-id id
                   :name    (pricing-impl/get-name plan-item)
                   :charge  (stripe-product-plan->charge stripe-product-plan)}
                  order (assoc :order order)
                  required-items (assoc :required-items required-items)
                  optional-items (assoc :optional-items optional-items))))
      stripe-product-prices)))

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