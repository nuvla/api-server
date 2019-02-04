(ns com.sixsq.slipstream.ssclj.resources.job-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as s]
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.job :refer :all]
    [com.sixsq.slipstream.ssclj.resources.job.utils :as ju]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.util.zookeeper :as uzk]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context resource-url))

(def valid-job
  {:resourceURI resource-uri
   :action      "collect"
   :acl         {:owner {:type "USER" :principal "admin"}
                 :rules [{:type "USER" :principal "jane" :right "VIEW"}]}})

(def zk-job-path-start-subs "/job/entries/entry-")

(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    (initialize)

    (is (uzk/exists ju/locking-queue-path))

    ;; anonymous create should fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-job))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; user create should fail
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-job))
        (ltu/body->edn)
        (ltu/is-status 403))

    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-job))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))
          job (-> session-user
                  (request abs-uri)
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-operation-present "stop")
                  (get-in [:response :body]))
          zookeeper-path (get-in job [:properties :zookeeper-path])]

      (is (= "QUEUED" (:state job)))

      (is (s/starts-with? zookeeper-path (str zk-job-path-start-subs "999-")))

      (is (= (uzk/get-data zookeeper-path) uri))

      (-> session-user
          (request "/api/job")
          (ltu/body->edn)
          (ltu/is-status 200)
          (get-in [:response :body]))

      (-> session-admin
          (request abs-uri :request-method :put
                   :body (json/write-str {:state ju/state-running}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value string? :started true))

      ;; set state to a final state make progress to set 100 automatically and set duration
      (-> session-admin
          (request abs-uri :request-method :put
                   :body (json/write-str {:state ju/state-success}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :progress 100)
          (ltu/is-key-value nat-int? :duration true))

      (-> session-admin
          (request abs-uri :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))

    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str (assoc valid-job :priority 50)))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))
          zookeeper-path (-> session-user
                             (request abs-uri)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-present "stop")
                             (get-in [:response :body :properties :zookeeper-path]))]
      (is (s/starts-with? zookeeper-path (str zk-job-path-start-subs "050-"))))
    ))
