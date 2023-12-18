(ns sixsq.nuvla.server.resources.job-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.job :as t]
    [sixsq.nuvla.server.resources.job.test-utils :as test-utils]
    [sixsq.nuvla.server.resources.job.utils :as ju]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [sixsq.nuvla.server.util.zookeeper :as uzk]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def valid-job
  {:resource-type t/resource-type
   :action        "collect"
   :acl           {:owners   ["group/nuvla-admin"]
                   :view-acl ["user/jane"]
                   :manage   ["user/jane"]}})


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")]

    (t/initialize)

    (is (uzk/exists ju/locking-queue-path))

    (testing "anonymous create should fail"
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-job))
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "user create should fail"
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-job))
          (ltu/body->edn)
          (ltu/is-status 403)))

    (let [uri        (-> session-admin
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str valid-job))
                         (ltu/body->edn)
                         (ltu/is-status 201)
                         (ltu/location))
          abs-uri    (str p/service-context uri)
          cancel-url (-> session-user
                         (request abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/is-operation-present ju/action-cancel)
                         (ltu/is-key-value (fn [job] (some #(str/starts-with? % "/job/entries") job)) :tags true)
                         (ltu/is-key-value :state "QUEUED")
                         (ltu/get-op-url ju/action-cancel))]

      (testing "user can cancel a job"
        (-> session-user
            (request cancel-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-absent ju/action-cancel)
            (ltu/is-key-value :progress 100)
            (ltu/is-key-value :state ju/state-canceled)
            (ltu/is-key-value nil? :started true)))

      (testing "edit is not allowed in a final state"
        (-> session-admin
            (request abs-uri
                     :request-method :put
                     :body (json/write-str {:state "SUCCESS"}))
            (ltu/body->edn)
            (ltu/is-status 409)
            (ltu/message-matches "edit is not allowed in final state")))

      (-> session-admin
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))

    (testing "Admin is able to create a job and set his priority"
      (let [job-id  (-> session-admin
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str (assoc valid-job :priority 50)))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))
            job-url (ltu/href->url job-id)]
        (testing "Setting state to running will also set the started timestamp"
          (-> session-admin
              (request job-url :request-method :put
                       :body (json/write-str {:state ju/state-running}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-absent ju/action-cancel)
              (ltu/is-key-value :state ju/state-running)
              (ltu/is-key-value :progress 0)
              (ltu/is-key-value string? :started true)))

        (testing "Bulk job cancel also children jobs"
          (let [bulk-job-resp (-> session-admin
                                  (request base-uri
                                           :request-method :post
                                           :body (json/write-str (assoc valid-job
                                                                   :action "bulk-action"
                                                                   :priority 50)))
                                  (ltu/body->edn)
                                  (ltu/is-status 201))
                bulk-job-id   (ltu/location bulk-job-resp)
                bulk-job-url  (ltu/location-url bulk-job-resp)]

            (testing "Cancel job with children"
              (let [cancel-url (-> session-admin
                                   (request bulk-job-url)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/is-operation-present ju/action-cancel)
                                   (ltu/get-op-url ju/action-cancel))]
                (-> session-admin
                    (request cancel-url)
                    (ltu/body->edn)
                    (ltu/is-status 200))

                (ltu/refresh-es-indices)

                (let [cancel-job (first (test-utils/query-jobs
                                          {:target-resource bulk-job-id
                                           :action          "cancel_children_jobs"
                                           :orderby         [["created" :desc]]
                                           :last            1}))]
                  (is (some? cancel-job))
                  (is (= (:priority cancel-job) 10)))))))))

    (testing "Job timeout"
      (let [job-resp (-> session-admin
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str valid-job))
                         (ltu/body->edn)
                         (ltu/is-status 201))
            job-url  (ltu/location-url job-resp)]

        (-> session-admin
            (request job-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-absent ju/action-timeout))

        (-> session-admin
            (request job-url
                     :request-method :put
                     :body (json/write-str {:state "RUNNING"}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [timeout-url (-> session-admin
                              (request job-url)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/is-operation-present ju/action-timeout)
                              (ltu/get-op-url ju/action-timeout))]

          (testing "simple user cannot call timeout"
            (-> session-user
                (request timeout-url)
                (ltu/body->edn)
                (ltu/is-status 403)))

          (-> session-admin
              (request timeout-url)
              (ltu/body->edn)
              (ltu/is-status 200)))))))
