(ns sixsq.nuvla.server.resources.subscription-config-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is join-fixtures use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.subscription :as sub]
    [sixsq.nuvla.server.resources.subscription-config :as t])
  (:import
    [java.util UUID]))


(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))


(deftest lifecycle
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        valid-subscription-config {:enabled         true
                                   :category        "notification"
                                   :method-ids       [(str "notification-method/" (str (UUID/randomUUID)))]
                                   :resource-kind   "infrastructure-service"
                                   :resource-filter "tags='foo'"
                                   :criteria        {:kind      "numeric"
                                                     :metric    "load"
                                                     :value     "75"
                                                     :condition ">"}
                                   :acl             {:owners ["user/jane"]}}]
    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit)))

    ;; anon query fails
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; anon create must fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-subscription-config))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; check creation
    (let [subs-uri (-> session-user
                       (request base-uri
                                :request-method :post
                                :body (json/write-str valid-subscription-config))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))

          subs-abs-uri (str p/service-context subs-uri)]

      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-resource-uri t/collection-type)
          (ltu/is-count 1))

      (-> session-admin
          (request subs-abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :enable)
          (ltu/is-operation-present :disable)
          (ltu/is-operation-present :set-notif-method-ids))

      ;; verify that an edit works
      (let [notif-ids [(str "notification-method/" (str (UUID/randomUUID)))]
            updated (assoc valid-subscription-config :method-ids notif-ids)]

        (-> session-user
            (request subs-abs-uri
                     :request-method :put
                     :body (json/write-str updated))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/body))

        (let [updated-body (-> session-admin
                               (request subs-abs-uri)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))]

          (is (= notif-ids (:method-ids updated-body)))))

      ;;
      ;; disable subscription
      (-> session-user
          (request (str subs-abs-uri "/" t/disable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      (is (= false (-> session-user
                       (request subs-abs-uri)
                       (ltu/body->edn)
                       (ltu/body)
                       :enabled)))

      ;;
      ;; enable subscription
      (-> session-user
          (request (str subs-abs-uri "/" t/enable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      (is (= true (-> session-user
                      (request subs-abs-uri)
                      (ltu/body->edn)
                      (ltu/body)
                      :enabled)))

      ;; set method-ids using operation
      (let [method-ids [(str "notification-method/" (str (UUID/randomUUID)))]]
        (-> session-user
            (request (str subs-abs-uri "/" t/set-notif-method-ids)
                     :request-method :post
                     :body (json/write-str {:method-ids method-ids}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (is (= method-ids (-> session-user
                             (request subs-abs-uri)
                             (ltu/body->edn)
                             (ltu/body)
                             :method-ids))))
      ;; delete
      (-> session-user
          (request subs-abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(defn create-monitored-resources
  [session-user valid-acl num-resources tag]
  (let [base-uri (str p/service-context infra-service/resource-type)

        service-name "my-service"
        service-desc "my-description"
        service-tags [tag]

        valid-service {:parent        "parent/00"
                       :subtype       "docker"
                       :endpoint      "https://docker.example.org/api"
                       :state         "STARTED"
                       :swarm-enabled true
                       :online        true}

        valid-create {:name        service-name
                      :description service-desc
                      :template    (merge {:href (str infra-service-tpl/resource-type "/"
                                                      infra-service-tpl-generic/method)}
                                          valid-service)}]
    ;; without the tag
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-create
                                         :name service-name
                                         :acl valid-acl)))
        (ltu/body->edn)
        (ltu/is-status 201))

    ;; with the tag
    (doseq [n (range num-resources)]
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-create
                                           :name (str service-name n)
                                           :tags service-tags
                                           :acl valid-acl)))
          (ltu/body->edn)
          (ltu/is-status 201)))))


(deftest create-with-individual-subscriptions-with-filter
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        acl {:owners ["user/jane"]}
        num-resources 3
        tag "FOO"
        _ (create-monitored-resources session-user acl num-resources tag)
        num-created (-> session-user
                        (request (str p/service-context infra-service/resource-type))
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/body)
                        :count)
        valid-subscription-config {:enabled         true
                                   :category        "notification"
                                   :method-ids       [(str "notification-method/" (str (UUID/randomUUID)))]
                                   :resource-kind   infra-service/resource-type
                                   :resource-filter (str "tags='" tag "'")
                                   :criteria        {:kind      "numeric"
                                                     :metric    "load"
                                                     :value     "75"
                                                     :condition ">"}
                                   :acl             acl}]

    ;; check creation of individual subscriptions
    (let [subs-base-uri (str p/service-context sub/resource-type)
          subs-before (-> session-user
                          (request subs-base-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/body)
                          :count)

          subs-conf-uri (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-subscription-config))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          subs-conf-abs-uri (str p/service-context subs-conf-uri)

          subs-after (-> session-user
                         (request subs-base-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/body)
                         :count)]
      (is (= num-resources (- subs-after subs-before)))

      ;;
      ;; disable subscription
      (-> session-user
          (request (str subs-conf-abs-uri "/" t/disable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check subscription
      (is (= false (-> session-user
                       (request subs-conf-abs-uri)
                       (ltu/body->edn)
                       (ltu/body)
                       :enabled)))

      ;; check individual subscriptions
      (let [filter (format "parent='%s'" subs-conf-uri)
            resources (-> session-user
                          (content-type "application/x-www-form-urlencoded")
                          (request subs-base-uri
                                   :request-method :put
                                   :body (rc/form-encode {:filter filter
                                                          :select ["enabled"]}))
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-count num-resources)
                          (ltu/body)
                          :resources)]
        (doseq [res resources]
          (is (= false (:enabled res)))))

      ;;
      ;; enable subscription
      (-> session-user
          (request (str subs-conf-abs-uri "/" t/enable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check subscription
      (is (= true (-> session-user
                      (request subs-conf-abs-uri)
                      (ltu/body->edn)
                      (ltu/body)
                      :enabled)))

      ;; check individual subscriptions
      (let [filter (format "parent='%s'" subs-conf-uri)
            resources (-> session-user
                          (content-type "application/x-www-form-urlencoded")
                          (request subs-base-uri
                                   :request-method :put
                                   :body (rc/form-encode {:filter filter
                                                          :select ["enabled"]}))
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-count num-resources)
                          (ltu/body)
                          :resources)]
        (doseq [res resources]
          (is (= true (:enabled res)))))

      ;; Delete subscription
      (-> session-user
          (request subs-conf-abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check individual subscriptions are deleted
      (-> session-user
          (content-type "application/x-www-form-urlencoded")
          (request subs-base-uri
                   :request-method :put
                   :body (rc/form-encode {:filter (format "parent='%s'" subs-conf-uri)
                                          :last   0}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 0)))))


(deftest create-with-individual-subscriptions-empty-filter
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        acl {:owners ["user/jane"]}
        _ (create-monitored-resources session-user acl 3 "FOO")
        num-created (-> session-user
                        (request (str p/service-context infra-service/resource-type))
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/body)
                        :count)
        valid-subscription-config {:enabled         true
                                   :category        "notification"
                                   :method-ids       [(str "notification-method/" (str (UUID/randomUUID)))]
                                   :resource-kind   infra-service/resource-type
                                   :resource-filter ""
                                   :criteria        {:kind      "numeric"
                                                     :metric    "load"
                                                     :value     "75"
                                                     :condition ">"}
                                   :acl             acl}]

    ;; check creation of individual subscriptions
    (let [subs-base-uri (str p/service-context sub/resource-type)
          subs-before (-> session-user
                          (request subs-base-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/body)
                          :count)

          subs-conf-uri (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-subscription-config))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          subs-conf-abs-uri (str p/service-context subs-conf-uri)

          subs-after (-> session-user
                         (request subs-base-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/body)
                         :count)]
      (is (= num-created (- subs-after subs-before)))

      ;;
      ;; disable subscription
      (-> session-user
          (request (str subs-conf-abs-uri "/" t/disable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check subscription
      (is (= false (-> session-user
                       (request subs-conf-abs-uri)
                       (ltu/body->edn)
                       (ltu/body)
                       :enabled)))

      ;; check individual subscriptions
      (let [filter (format "parent='%s'" subs-conf-uri)
            resources (-> session-user
                          (content-type "application/x-www-form-urlencoded")
                          (request subs-base-uri
                                   :request-method :put
                                   :body (rc/form-encode {:filter filter
                                                          :select ["enabled"]}))
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-count num-created)
                          (ltu/body)
                          :resources)]
        (doseq [res resources]
          (is (= false (:enabled res)))))

      ;;
      ;; enable subscription
      (-> session-user
          (request (str subs-conf-abs-uri "/" t/enable)
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check subscription
      (is (= true (-> session-user
                      (request subs-conf-abs-uri)
                      (ltu/body->edn)
                      (ltu/body)
                      :enabled)))

      ;; check individual subscriptions
      (let [filter (format "parent='%s'" subs-conf-uri)
            resources (-> session-user
                          (content-type "application/x-www-form-urlencoded")
                          (request subs-base-uri
                                   :request-method :put
                                   :body (rc/form-encode {:filter filter
                                                          :select ["enabled"]}))
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-count num-created)
                          (ltu/body)
                          :resources)]
        (doseq [res resources]
          (is (= true (:enabled res)))))

      ;; Delete subscription
      (-> session-user
          (request subs-conf-abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check individual subscriptions are deleted
      (-> session-user
          (content-type "application/x-www-form-urlencoded")
          (request subs-base-uri
                   :request-method :put
                   :body (rc/form-encode {:filter (format "parent='%s'" subs-conf-uri)
                                          :last   0}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 0)))))


(deftest editing
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "user/jane group/nuvla-user group/nuvla-anon")

        acl {:owners ["user/jane"]}
        _ (create-monitored-resources session-user acl 3 "FOO")
        num-created (-> session-user
                        (request (str p/service-context infra-service/resource-type))
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/body)
                        :count)
        valid-subscription-config {:enabled         true
                                   :category        "notification"
                                   :method-ids       [(str "notification-method/" (str (UUID/randomUUID)))]
                                   :resource-kind   infra-service/resource-type
                                   :resource-filter ""
                                   :criteria        {:kind      "numeric"
                                                     :metric    "load"
                                                     :value     "75"
                                                     :condition ">"}
                                   :acl             acl}]

    ;; check creation of individual subscriptions
    (let [subs-base-uri (str p/service-context sub/resource-type)
          subs-before (-> session-user
                          (request subs-base-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/body)
                          :count)

          subs-conf-uri (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-subscription-config))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          subs-conf-abs-uri (str p/service-context subs-conf-uri)

          subs-after (-> session-user
                         (request subs-base-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/body)
                         :count)]
      (is (= num-created (- subs-after subs-before)))

      ;;
      ;; editing allowed fields updates fields on individual subscriptions as well
      (let [current (-> session-user
                        (request subs-conf-abs-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/body))
            new-criteria {:kind      "numeric"
                          :metric    "disk"
                          :value     "85"
                          :condition "<"}
            to-update (merge current {:name        "new name"
                                      :description "new description"
                                      :criteria new-criteria})
            updated (-> session-user
                        (request subs-conf-abs-uri
                                 :request-method :put
                                 :body (json/write-str to-update))
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/body))]
        (is (= "new name" (:name updated)))
        (is (= "new description" (:description updated)))
        (is (= new-criteria (:criteria updated)))

        ;; check individual subscriptions
        (let [resources (-> session-user
                            (content-type "application/x-www-form-urlencoded")
                            (request subs-base-uri
                                     :request-method :put
                                     :body (rc/form-encode {:filter (format "parent='%s'" subs-conf-uri)}))
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-count num-created)
                            (ltu/body)
                            :resources)]
          (doseq [res resources]
            (is (= "new name" (:name res)))
            (is (= "new description" (:description res)))
            (is (= new-criteria (:criteria res))))))

      ;;
      ;; editing NOT allowed fields does nothing
      (let [current (-> session-user
                        (request subs-conf-abs-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/body))
            new-criteria {:kind      "numeric"
                          :metric    "disk"
                          :value     "85"
                          :condition "<"}
            to-update (merge current {:resource-filter "new filter"
                                      :resource-kind   "new resource kind"})
            updated (-> session-user
                        (request subs-conf-abs-uri
                                 :request-method :put
                                 :body (json/write-str to-update))
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        (ltu/body))]
        (is (= "" (:resource-filter updated)))
        (is (= infra-service/resource-type (:resource-kind updated)))

        ;; check individual subscriptions
        (let [resources (-> session-user
                            (content-type "application/x-www-form-urlencoded")
                            (request subs-base-uri
                                     :request-method :put
                                     :body (rc/form-encode {:filter (format "parent='%s'" subs-conf-uri)}))
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-count num-created)
                            (ltu/body)
                            :resources)]
          (doseq [res resources]
            (is (= "" (:resource-filter res)))
            (is (= infra-service/resource-type (:resource-kind res))))))

      ;; Delete subscription
      (-> session-user
          (request subs-conf-abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; check individual subscriptions are deleted
      (-> session-user
          (content-type "application/x-www-form-urlencoded")
          (request subs-base-uri
                   :request-method :put
                   :body (rc/form-encode {:filter (format "parent='%s'" subs-conf-uri)
                                          :last   0}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 0)))))