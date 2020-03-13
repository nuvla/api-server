(ns sixsq.nuvla.server.resources.job-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.job :as t]
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
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")]

    (t/initialize)

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

    (let [uri            (-> session-admin
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-job))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))
          abs-uri        (str p/service-context uri)]

      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :stop)
          (ltu/is-key-value :state "QUEUED"))

      (-> session-user
          (request "/api/job")
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/body))

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

    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-job :priority 50)))
        (ltu/body->edn)
        (ltu/is-status 201))))
