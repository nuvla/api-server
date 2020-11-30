(ns sixsq.nuvla.server.resources.infrastructure-service-coe-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures testing]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [sixsq.nuvla.server.resources.infrastructure-service :as t]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as service-group]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-coe :as infra-service-tpl-coe]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [ring.util.codec :as rc]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def service-group-base-uri (str p/service-context service-group/resource-type))


(def credential-base-uri (str p/service-context credential/resource-type))


(def valid-acl {:owners   ["group/nuvla-admin"]
                :view-acl ["group/nuvla-user"]})


(deftest check-metadata
  (mdtu/check-metadata-exists (str infra-service-tpl/resource-type "-" infra-service-tpl-coe/method)
                              (str infra-service-tpl/resource-type "-" infra-service-tpl-coe/method "-create")))


(defn set-state-on-is
  [abs-uri session state]
  (-> session
      (request abs-uri :request-method :put
               :body (json/write-str {:state state}))
      (ltu/body->edn)
      (ltu/is-status 200)))


(def COE_SUBTYPES #{"swarm" "kubernetes"})


(deftest lifecycle
  (doseq [subtype COE_SUBTYPES]
    (let [session-anon        (-> (ltu/ring-app)
                                  session
                                  (content-type "application/json"))
          session-admin       (header session-anon authn-info-header
                                      "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-user        (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

          ;; setup a service-group to act as parent for service
          valid-service-group {:name          "my-service-group"
                               :description   "my-description"
                               :documentation "http://my-documentation.org"}

          service-group-id    (-> session-user
                                  (request service-group-base-uri
                                           :request-method :post
                                           :body (json/write-str valid-service-group))
                                  (ltu/body->edn)
                                  (ltu/is-status 201)
                                  (ltu/location))

          ;; setup a credential (not the right type) to reference
          href                (str ct/resource-type "/" akey/method)

          create-import-href  {:name        "my-credential"
                               :description "my-credential-description"
                               :tags        ["one" "two"]
                               :template    {:href href
                                             :ttl  1000}}

          credential-id       (-> session-user
                                  (request credential-base-uri
                                           :request-method :post
                                           :body (json/write-str create-import-href))
                                  (ltu/body->edn)
                                  (ltu/is-status 201)
                                  (ltu/location))

          service-name        "my-service"
          service-desc        "my-description"
          service-tags        ["alpha" "beta" "gamma"]

          valid-create        {:name        service-name
                               :description service-desc
                               :tags        service-tags
                               :template    {:href     (str infra-service-tpl/resource-type "/"
                                                            infra-service-tpl-coe/method)
                                             :method   infra-service-tpl-coe/method
                                             :parent   service-group-id
                                             :subtype  subtype
                                             :management-credential credential-id}}]

      ;; anon create must fail
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-create))
          (ltu/body->edn)
          (ltu/is-status 400))

      ;; check creation
      (doseq [session [session-admin session-user]]
        (let [uri     (-> session
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str valid-create))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))
              abs-uri (str p/service-context uri)]

          ;; verify contents
          (let [service (-> session
                            (request abs-uri)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-operation-present :edit)
                            (ltu/body))]

            (is (= 1 (count (:operations service))))
            (is (= service-name (:name service)))
            (is (= service-desc (:description service)))
            (is (= service-tags (:tags service)))
            (is (:subtype service))
            (is (nil? (:endpoint service)))
            (is (nil? (:swarm-enabled service)))
            (is (nil? (:online service)))
            (is (= "STARTING" (:state service)))
            (is (= credential-id (:management-credential service))))

          ;; can NOT delete resource in STARTING state
          (-> session
              (request abs-uri :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 409))

          ;; set TERMINATED state
          (set-state-on-is abs-uri session "TERMINATED")

          ;; can delete resource in TERMINATED state
          (-> session
              (request abs-uri :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200)))))))


;; Validate right CRUD operations and actions are available on resource in
;; different states of the state machine.
(deftest states-and-operations
  (doseq [subtype COE_SUBTYPES]
    (testing "STARTING -> STARTED -> STOPPING -> STOPPED -> TERMINATING -> ERROR -> TERMINATED -> resource deleted"
       (let [session-anon        (-> (ltu/ring-app)
                                     session
                                     (content-type "application/json"))
             session-user        (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

             ;; setup a service-group to act as parent for service
             valid-service-group {:name          "my-service-group"
                                  :description   "my-description"
                                  :documentation "http://my-documentation.org"}

             service-group-id    (-> session-user
                                     (request service-group-base-uri
                                              :request-method :post
                                              :body (json/write-str valid-service-group))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/location))

             ;; setup a credential (not the right type) to reference
             href                (str ct/resource-type "/" akey/method)

             create-import-href  {:name        "my-credential"
                                  :description "my-credential-description"
                                  :tags        ["one" "two"]
                                  :template    {:href href
                                                :ttl  1000}}

             credential-id       (-> session-user
                                     (request credential-base-uri
                                              :request-method :post
                                              :body (json/write-str create-import-href))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/location))

             service-name        "my-service"
             service-desc        "my-description"
             service-tags        ["alpha" "beta" "gamma"]

             valid-create        {:name        service-name
                                  :description service-desc
                                  :tags        service-tags
                                  :template    {:href     (str infra-service-tpl/resource-type "/"
                                                               infra-service-tpl-coe/method)
                                                :method   infra-service-tpl-coe/method
                                                :parent   service-group-id
                                                :subtype  subtype
                                                :management-credential credential-id}}
             uri (-> session-user
                     (request base-uri
                              :request-method :post
                              :body (json/write-str valid-create))
                     (ltu/body->edn)
                     (ltu/is-status 201)
                     (ltu/location))
             abs-uri (str p/service-context uri)
             check-event (fn [exp-state]
                           (let [filter (format "category='state' and content/resource/href='%s' and content/state='%s'" uri exp-state)
                                 state (-> session-user
                                           (content-type "application/x-www-form-urlencoded")
                                           (request "/api/event"
                                                    :request-method :put
                                                    :body (rc/form-encode {:filter filter}))
                                           (ltu/body->edn)
                                           (ltu/is-status 200)
                                           (ltu/is-count 1)
                                           (ltu/body)
                                           :resources
                                           first
                                           :content
                                           :state)]
                             (is (= state exp-state))))]

             ;; STARTING: edit
             (let [response (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                   service (ltu/body response)]
               (is (= "STARTING" (:state service)))
               (is (= 1 (count (:operations service))))
               (ltu/is-operation-present response :edit))

             ;; check event for STARTING was created
             (check-event "STARTING")

             ;; set STARTED state
             (set-state-on-is abs-uri session-user "STARTED")

             ;; check event for STARTED was created
             (check-event "STARTED")

             ;; STARTED: edit, stop, terminate
             (let [response (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                   service (ltu/body response)]
               (is (= "STARTED" (:state service)))
               (is (= 3 (count (:operations service))))
               (ltu/is-operation-present response :edit)
               (ltu/is-operation-present response :stop)
               (ltu/is-operation-present response :terminate))

             ;; call 'stop' action to enter STOPPING state
             (let [op-uri (-> session-user
                              (request abs-uri)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/get-op-url "stop"))]
               (-> session-user
                   (request op-uri
                            :request-method :post)
                   (ltu/is-status 202)
                   (ltu/body->edn)))

             ;; check event for STOPPING was created
             (check-event "STOPPING")

             ;; STOPPING: edit
             (let [response (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                   service (ltu/body response)]
               (is (= "STOPPING" (:state service)))
               (is (= 1 (count (:operations service))))
               (ltu/is-operation-present response :edit))

             ;; set STOPPED state manually (there is no job to do that)
             (set-state-on-is abs-uri session-user "STOPPED")

             ;; check event for STOPPED was created
             (check-event "STOPPED")

             ;; STOPPED: edit, start, terminate
             (let [response (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                   service (ltu/body response)]
               (is (= "STOPPED" (:state service)))
               (is (= 3 (count (:operations service))))
               (ltu/is-operation-present response :edit)
               (ltu/is-operation-present response :start)
               (ltu/is-operation-present response :terminate))

             ;; call terminate action to enter 'TERMINATING' state
             (let [op-uri (-> session-user
                              (request abs-uri)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/get-op-url "terminate"))]
               (-> session-user
                   (request op-uri
                            :request-method :post)
                   (ltu/is-status 202)
                   (ltu/body->edn)))

             ;; check event for TERMINATING was created
             (check-event "TERMINATING")

             ;; TERMINATING: edit
             (let [response (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                   service (ltu/body response)]
               (is (= "TERMINATING" (:state service)))
               (is (= 1 (count (:operations service))))
               (ltu/is-operation-present response :edit))

             ;;; set ERROR state to fake an error
             (set-state-on-is abs-uri session-user "ERROR")

             ;; check event for ERROR was created
             (check-event "ERROR")

             ;; ERROR: edit, terminate
             (let [response (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                   service (ltu/body response)]
               (is (= "ERROR" (:state service)))
               (is (= 2 (count (:operations service))))
               (ltu/is-operation-present response :edit)
               (ltu/is-operation-present response :terminate))

             ;; set TERMINATED state
             (set-state-on-is abs-uri session-user "TERMINATED")

             ;; check event for TERMINATED was created
             (check-event "TERMINATED")

             ;; TERMINATED: edit, delete
             (let [response (-> session-user
                                (request abs-uri)
                                (ltu/body->edn)
                                (ltu/is-status 200))
                   service (ltu/body response)]
               (is (= "TERMINATED" (:state service)))
               (is (= 2 (count (:operations service))))
               (ltu/is-operation-present response :edit)
               (ltu/is-operation-present response :delete))

             ;; can delete resource in TERMINATED state
             (-> session-user
                 (request abs-uri :request-method :delete)
                 (ltu/body->edn)
                 (ltu/is-status 200))))))
