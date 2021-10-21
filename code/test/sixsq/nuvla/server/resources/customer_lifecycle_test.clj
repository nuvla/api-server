(ns sixsq.nuvla.server.resources.customer-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest use-fixtures]]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.user-utils-test :as user-utils-test]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.customer :as t]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.pricing :as pricing]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture
              (partial user-utils-test/with-existing-user "tarzan@example.com"))


(def base-uri (str p/service-context t/resource-type))


(def valid-entry {:fullname     "toto"
                  :address      {:street-address "Av. quelque chose"
                                 :city           "Meyrin"
                                 :country        "CH"
                                 :postal-code    "1217"}
                  :subscription {:plan-id       "price_1GzO4WHG9PNMTNBOSfypKuEa"
                                 :plan-item-ids ["price_1GzO8HHG9PNMTNBOWuXQm9zZ"
                                                 "price_1GzOC6HG9PNMTNBOEb5819lm"
                                                 "price_1GzOfLHG9PNMTNBO0l2yDtPS"]}})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (if-not (env/env :stripe-api-key)
    (log/error "Customer lifecycle is not tested because lack of stripe-api-key!")
    (let [session-anon    (-> (session (ltu/ring-app))
                              (content-type "application/json"))
          session-admin   (header session-anon authn-info-header
                                  "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user    (header session-anon authn-info-header (str @user-utils-test/user-id! " " @user-utils-test/user-id! " group/nuvla-user group/nuvla-anon"))
          session-group-a (header session-anon authn-info-header "group/a  group/a group/nuvla-user group/nuvla-anon")]

      ;; admin create pricing catalogue
      (-> session-admin
          (request (str p/service-context pricing/resource-type)
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 201))

      ;; create: NOK for anon
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; create: NOK for admin
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"Admin can't create customer!"))

      ;; creation should list all required-items
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (-> valid-entry
                             (update-in [:subscription :plan-item-ids] pop)
                             json/write-str))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"Plan-item-ids not valid for plan.*"))

      ;; creation should list all required-items
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (-> valid-entry
                             (update-in [:subscription :plan-item-ids] conj "price_itemExtra")
                             json/write-str))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"Plan-item-ids not valid for plan.*"))

      ;; undefined plan
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (-> valid-entry
                             (assoc-in [:subscription :plan-id] "price_notExist")
                             json/write-str))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"Plan-id .* not found!"))

      ;; add with unknown coupon code fail
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (-> valid-entry
                             (assoc :coupon "doesn't-exist")
                             json/write-str))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"No such coupon.*"))

      (-> session-group-a
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 400)
          (ltu/message-matches #"Customer email is mandatory for group!"))

      (let [customer-1 (-> session-user
                           (request base-uri
                                    :request-method :post
                                    :body (json/write-str valid-entry))
                           (ltu/body->edn)
                           (ltu/is-status 201)
                           (ltu/location-url))]

        (let [customer-response   (-> session-user
                                      (request customer-1)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present :customer-info)
                                      (ltu/is-operation-present :update-customer)
                                      (ltu/is-operation-present :get-subscription)
                                      (ltu/is-operation-present :create-subscription)
                                      (ltu/is-operation-present :create-setup-intent)
                                      (ltu/is-operation-present :list-payment-methods)
                                      (ltu/is-operation-present :set-default-payment-method)
                                      (ltu/is-operation-present :detach-payment-method)
                                      (ltu/is-operation-present :upcoming-invoice)
                                      (ltu/is-operation-present :list-invoices)
                                      (ltu/is-operation-present :add-coupon)
                                      (ltu/is-operation-present :remove-coupon)
                                      (ltu/is-key-value
                                        #(str/starts-with? % "cus_") :customer-id true)
                                      (ltu/is-key-value
                                        #(str/starts-with? % "sub_") :subscription-id true))
              create-setup-intent (ltu/get-op-url customer-response :create-setup-intent)
              add-coupon          (ltu/get-op-url customer-response :add-coupon)]

          (-> session-user
              (request create-setup-intent)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value some? :client-secret true))

          (-> session-user
              (request add-coupon
                       :request-method :post
                       :body (json/write-str {:coupon "doesn't-exist"}))
              (ltu/body->edn)
              (ltu/is-status 400)))

        (doseq [{:keys [customer-id]} (-> session-user
                                          (request base-uri
                                                   :request-method :put)
                                          (ltu/body->edn)
                                          (ltu/is-status 200)
                                          (ltu/is-count 1)
                                          (ltu/entries))]
          (-> customer-id
              pricing-impl/retrieve-customer
              pricing-impl/delete-customer))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))



