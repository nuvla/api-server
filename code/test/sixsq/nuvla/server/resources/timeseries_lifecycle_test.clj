(ns sixsq.nuvla.server.resources.timeseries-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.timeseries :as t]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest insert
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        entry         {:dimensions [{:field-name "test-dimension"
                                     :field-type "keyword"}]
                       :metrics    [{:field-name  "test-metric"
                                     :field-type  "long"
                                     :metric-type "gauge"}]}
        ts-id         (-> session-admin
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str entry))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))
        ts-url        (str p/service-context ts-id)
        ts-resource   (-> session-admin
                          (request ts-url)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/body))
        ts            (db/retrieve-timeseries (t/resource-id->timeseries-id ts-id))]
    (is (= (assoc entry
             :id ts-id
             :resource-type "timeseries")
           (select-keys ts-resource [:resource-type :id :dimensions :metrics])))
    (is (pos? (count (:data_streams ts))))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[resource-uri :put]
                            [resource-uri :post]])))
