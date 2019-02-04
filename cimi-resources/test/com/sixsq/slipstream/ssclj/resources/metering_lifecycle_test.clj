(ns com.sixsq.slipstream.ssclj.resources.metering-lifecycle-test
  (:require
    [cemerick.url :as url]
    [clj-time.core :as time]
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.metering :as m]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase m/resource-url)))

(def valid-acl {:owner {:principal "ADMIN",
                        :type      "ROLE"},
                :rules [{:principal "realm:accounting_manager",
                         :type      "ROLE",
                         :right     "VIEW"},
                        {:principal "test",
                         :type      "USER",
                         :right     "VIEW"},
                        {:principal "cern:cern",
                         :type      "ROLE",
                         :right     "VIEW"},
                        {:principal "cern:my-accounting-group",
                         :type      "ROLE",
                         :right     "VIEW"}]})

(deftest lifecycle
  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "root ADMIN USER ANON"))]

    ;; admin user collection query should succeed but be empty (no  records created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; create a metering
    (let [timestamp "1964-08-25T10:00:00.0Z"
          create-test-metering {:id            (str m/resource-url "/uuid")
                                :resourceURI   m/resource-uri
                                :created       timestamp
                                :updated       timestamp
                                :acl           valid-acl

                                :name          "short name"
                                :description   "short description",
                                :properties    {:a "one",
                                                :b "two"}

                                :instanceID    "aaa-bbb-111"
                                :connector     {:href "connector/0123-4567-8912"}
                                :state         "Running"
                                :billable      true
                                :ip            "127.0.0.1"

                                :credentials   [{:href  "credential/0123-4567-8912",
                                                 :roles ["realm:cern", "realm:my-accounting-group"]
                                                 :users ["long-user-id-1", "long-user-id-2"]}
                                                ]
                                :deployment    {:href "run/aaa-bbb-ccc",
                                                :user {:href "user/test"}}

                                :serviceOffer  {:href                  "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"
                                                :resource:vcpu         1
                                                :resource:ram          4096
                                                :resource:disk         10
                                                :resource:instanceType "Large"}

                                :snapshot-time timestamp}

          create-jane-metering (assoc create-test-metering :deployment {:href "run/444-555-666"
                                                                        :user {:href "user/jane"}})

          resp-test (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-test-metering))
                        (ltu/body->edn)
                        (ltu/is-status 201))

          resp-jane (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-jane-metering))
                        (ltu/body->edn)
                        (ltu/is-status 201))

          id-test (get-in resp-test [:response :body :resource-id])
          id-jane (get-in resp-jane [:response :body :resource-id])

          location-test (str p/service-context (-> resp-test ltu/location))
          location-jane (str p/service-context (-> resp-jane ltu/location))

          test-uri (str p/service-context id-test)
          jane-uri (str p/service-context id-jane)]

      (is (= location-test test-uri))
      (is (= location-jane jane-uri))

      ;; admin should be able to see everyone's records
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))

      (-> session-admin
          (request jane-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))


      ;; check contents and editing
      (let [reread-test-vm (-> session-admin
                               (request test-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               :response
                               :body)]

        (is (= (ltu/strip-unwanted-attrs reread-test-vm) (ltu/strip-unwanted-attrs create-test-metering)))

        (let [edited-test-vm (-> session-admin
                                 (request test-uri
                                          :request-method :put
                                          :body (json/write-str (assoc reread-test-vm :state "UPDATED!")))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 :response
                                 :body)]

          (is (= (assoc (ltu/strip-unwanted-attrs reread-test-vm) :state "UPDATED!")
                 (ltu/strip-unwanted-attrs edited-test-vm)))))


      ;; search
      (-> session-admin
          (request base-uri
                   :request-method :put
                   :body (json/write-str create-test-metering))
          (ltu/body->edn)
          (ltu/is-count 2)
          (ltu/is-status 200))


      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      (-> session-admin
          (request jane-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;;record should be deleted
      (-> session-admin
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods

  (let [resource-uri (str p/service-context (u/new-resource-id m/resource-name))]
    (doall
      (for [[uri method] [[base-uri :options]
                          [base-uri :delete]
                          [resource-uri :options]
                          [resource-uri :post]]]
        (-> (session (ltu/ring-app))
            (request uri
                     :request-method method
                     :body (json/write-str {:dummy "value"}))
            (ltu/is-status 405))))))


(defn insert-meter
  [session uri doc]
  (-> session
      (request uri
               :request-method :post
               :body (json/write-str doc))))

(deftest insertions
  (let [session-admin (-> (session (ltu/ring-app))
                          (content-type "application/json")
                          (header authn-info-header "root ADMIN USER ANON"))
        timestamp (time/now)
        vm-template {:id           (str m/resource-url "/uuid")
                     :resourceURI  m/resource-uri
                     :created      (str timestamp)
                     :updated      (str timestamp)
                     :acl          valid-acl

                     :name         "short name"
                     :description  "short description",
                     :properties   {:a "one",
                                    :b "two"}

                     :instanceID   "aaa-bbb-111"
                     :connector    {:href "connector/0123-4567-8912"}
                     :state        "Running"
                     :billable     true
                     :ip           "127.0.0.1"

                     :credentials  [{:href  "credential/0123-4567-8912",
                                     :roles ["realm:cern", "realm:my-accounting-group"]
                                     :users ["long-user-id-1", "long-user-id-2"]}]
                     :deployment   {:href "run/aaa-bbb-ccc",
                                    :user {:href "user/test"}}

                     :serviceOffer {:href                  "service-offer/e3db10f4-ad81-4b3e-8c04-4994450da9e3"
                                    :resource:vcpu         1
                                    :resource:ram          4096
                                    :resource:disk         10
                                    :resource:instanceType "Large"}}

        n 10
        snaps-minutes (map #(assoc vm-template :snapshot-time (str (time/plus timestamp (time/minutes %)))) (range 1 (inc n)))
        snaps-days (map #(assoc vm-template :snapshot-time (str (time/plus timestamp (time/days %)))) (range 1 (inc n)))]

    (doall (map (partial insert-meter session-admin base-uri) snaps-minutes))
    (doall (map (partial insert-meter session-admin base-uri) snaps-days))

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-count (* 2 n))
        (ltu/is-status 200))

    ;;check aggregations

    (-> session-admin
        (content-type "application/x-www-form-urlencoded")
        (request base-uri
                 :request-method :put
                 :body (url/map->query {:$first 1, :$last 0, :$aggregation "count:id"}))
        (ltu/body->edn)
        (ltu/is-count (* 2 n))
        (ltu/is-status 200))


    (let [agg-meter (fn agg-meter
                      [agg-query & [from to]]
                      (let [query-map {:$first 1, :$last 0, :$aggregation agg-query}]
                        (-> session-admin
                            (content-type "application/x-www-form-urlencoded")
                            (request base-uri
                                     :request-method :put
                                     :body (url/map->query
                                             (if (and from to)
                                               (assoc query-map :$filter (str "snapshot-time >= " from " and snapshot-time <= " to))
                                               query-map)))
                            (ltu/body->edn)
                            :response
                            :body
                            :aggregations
                            ((keyword agg-query))
                            :value)))]

      (are [query v] (is (= v (agg-meter query)))
                     "avg:serviceOffer/resource:ram" (double 4096)
                     "avg:serviceOffer/resource:disk" (double 10)
                     "count:id" (* 2 n)
                     "sum:serviceOffer/resource:vcpu" (double (* 2 n)))

      (let [after-3days (time/plus timestamp (time/days 3))
            after-1day (time/plus timestamp (time/days 1))]

        (are [query from to v] (is (= v (agg-meter query from to)))
                               "count:id" after-1day after-3days 3
                               "count:id" after-1day nil 20 ;; 'from' without 'to' is ignored
                               "count:id" (time/now) after-1day 11
                               "count:id" (time/now) after-3days 13
                               "sum:serviceOffer/resource:disk" after-1day after-3days (double 30))))))
