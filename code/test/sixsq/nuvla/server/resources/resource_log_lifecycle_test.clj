(ns sixsq.nuvla.server.resources.resource-log-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]
     :rename {session session-base}]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment.utils :as dp-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.resource-log :as t]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))


(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")

(def deployment-id "deployment/9e8214b0-625b-49be-b857-06e60c97c054")

(def parameter-name "param1")

(def user-id "user/jane")

(defn valid-entry-fn
  [parent-id]
  {:name       parameter-name
   :parent     parent-id
   :components []
   :log        {:c1 ["log1"] :c2 ["log2" "log22"]}
   :acl        {:owners   ["group/nuvla-admin"]
                :edit-acl [user-id]}})

(def session (-> (ltu/ring-app)
                 session-base
                 (content-type "application/json")))

(def session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))
(def session-jane (header session authn-info-header (str "user/jane user/jane group/nuvla-user group/nuvla-anon" " " session-id)))
(def session-other (header session authn-info-header "user/other user/other group/nuvla-user group/nuvla-anon"))
(def session-anon (header session authn-info-header "user/unknown user/unknown group/nuvla-anon"))


(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))


(deftest test-parent->action-name
  (are [result parent] (= result (t/parent->action-name {:parent parent}))
                       t/fetch-nuvlabox-log "nuvlabox/9e8214b0-625b-49be-b857-06e60c97c053"
                       t/fetch-deployment-log deployment-id))


(deftest lifecycle-nuvlabox
  (let [parent-id        (-> session-jane
                             (request nuvlabox-base-uri
                                      :request-method :post
                                      :body (json/write-str
                                              {:owner user-id}))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

        nuvlabox         (-> session-jane
                             (request (str p/service-context parent-id))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/body))

        session-nuvlabox (header session authn-info-header
                                 (str parent-id " " parent-id
                                      " group/nuvla-nuvlabox group/nuvla-anon"))

        valid-entry      (valid-entry-fn parent-id)]

    ;; admin nuvlabox-log collection query should succeed but be empty (no logs created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; normal nuvlabox-log collection query should succeed but be empty (no logs created yet)
    (-> session-jane
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-absent :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; anonymous nuvlabox-log collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; creation should only be allowed by admin; others get 403
    (doseq [session [session-anon session-jane session-other]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; create a nuvlabox log as an admin user using internal utility function
    (let [resp-test     {:response (nb/create-log nuvlabox {:body {:components ["agent"]}})}

          id-test       (ltu/body-resource-id resp-test)

          location-test (str p/service-context (-> resp-test ltu/location))

          test-uri      (str p/service-context id-test)]

      (is (= location-test test-uri))

      ;; admin should be able to see everyone's logs.
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-present :edit))

      ;; other users should not see the logs
      (-> session-other
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; user can manage, but not edit directly
      (let [bad-id    "nuvlabox/324c6138-0484-34b5-bf35-af3ad15815db"
            resp      (-> session-jane
                          (request test-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-operation-present :fetch)
                          (ltu/is-operation-absent :edit)
                          (ltu/is-operation-present :delete))

            fetch-url (ltu/get-op-url resp "fetch")

            original  (ltu/body resp)]

        ;; user cannot edit
        (-> session-jane
            (request test-uri
                     :request-method :put
                     :body (json/write-str {:parent         bad-id
                                            :name           "updated-name"
                                            :last-timestamp "1974-08-25T10:00:00.00Z"
                                            :log            {:c3 ["log3"]}}))
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; but the NB can
        (-> session-nuvlabox
            (request test-uri
                     :request-method :put
                     :body (json/write-str {:parent         bad-id
                                            :name           "updated-name"
                                            :last-timestamp "1974-08-25T10:00:00.00Z"
                                            :log            {:c3 ["log3"]}}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [{:keys [id name log last-timestamp]} (-> session-jane
                                                       (request test-uri)
                                                       (ltu/body->edn)
                                                       (ltu/is-status 200)
                                                       (ltu/body))]

          (is (= id (:id original)))
          (is (not= bad-id id))

          (is (not= name (:name original)))
          (is (= name "updated-name"))

          (is (= last-timestamp "1974-08-25T10:00:00.00Z"))

          (is (= log {:c3 ["log3"]})))

        ;; check the actions
        (let [job-url         (-> session-jane
                                  (request fetch-url
                                           :request-method :post)
                                  (ltu/body->edn)
                                  (ltu/is-status 202)
                                  (ltu/is-key-value #(-> % (str/split #" ") first) :message "starting")
                                  (ltu/location-url))
              get-context-url (-> session-admin
                                  (request job-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present :get-context)
                                  (ltu/get-op-url :get-context))
              context-keys    (-> session-admin
                                  (request get-context-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/body)
                                  keys)]
          (is (every? #{(keyword (:id nuvlabox)) (keyword (:id original))} context-keys)))

        (-> session-other
            (request fetch-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; check the fetch action doesn't create additional job if another one is pending
        (-> session-jane
            (request fetch-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 202)
            (ltu/is-key-value #(-> % (str/split #" ") first) :message "existing")))

      (-> session-anon
          (request test-uri
                   :request-method :put
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; search
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; logs can be deleted by user
      (-> session-jane
          (request test-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; resource should be deleted
      (-> session-admin
          (request test-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest lifecycle-deployment
  (let [valid-entry     (valid-entry-fn deployment-id)
        fake-deployment {:id  deployment-id
                         :acl {:owners [user-id]}}]
    (with-redefs [t/retrieve-parent-resource (constantly fake-deployment)]
      ;; admin deployment-log collection query should succeed but be empty (no logs created yet)
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit))

      ;; normal deployment-log collection query should succeed but be empty (no logs created yet)
      (-> session-jane
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-absent :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit))

      ;; anonymous deployment-log collection query should not succeed
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; creation should only be allowed by admin; others get 403
      (doseq [session [session-anon session-jane session-other]]
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str valid-entry))
            (ltu/body->edn)
            (ltu/is-status 403)))

      ;; create a deployment log as an admin user using internal utility function
      (let [resp-test     {:response (dp-utils/create-log
                                       fake-deployment
                                       {:nuvla/authn {:claims [session-id]}
                                        :body        {:components ["s1"]}})}

            id-test       (ltu/body-resource-id resp-test)

            location-test (str p/service-context (-> resp-test ltu/location))

            test-uri      (str p/service-context id-test)]

        (is (= location-test test-uri))

        ;; admin should be able to see everyone's logs.
        (-> session-admin
            (request test-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit))

        ;; other users should not see the logs
        (-> session-other
            (request test-uri)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; user can edit, but parent and service cannot be changed
        (let [bad-id    "deployment/324c6138-0484-34b5-bf35-af3ad15815db"
              resp      (-> session-jane
                            (request test-uri)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/is-operation-present :fetch)
                            (ltu/is-operation-present :edit)
                            (ltu/is-operation-present :delete))

              fetch-url (ltu/get-op-url resp "fetch")

              original  (ltu/body resp)]

          (-> session-jane
              (request test-uri
                       :request-method :put
                       :body (json/write-str {:parent         bad-id
                                              :name           "updated-name"
                                              :components     ["s1"]
                                              :last-timestamp "1964-08-25T10:00:00.00Z"
                                              :log            {:s1 ["log1"]}}))
              (ltu/body->edn)
              (ltu/is-status 200))

          (let [{:keys [id name service log last-timestamp]} (-> session-jane
                                                                 (request test-uri)
                                                                 (ltu/body->edn)
                                                                 (ltu/is-status 200)
                                                                 (ltu/body))]

            (is (= id (:id original)))
            (is (not= bad-id id))

            (is (not= name (:name original)))
            (is (= name "updated-name"))

            (is (= service (:service original)))
            (is (not= service "bad-service"))

            (is (= last-timestamp "1964-08-25T10:00:00.00Z"))

            (is (= log {:s1 ["log1"]})))

          ;; check the actions
          (let [job-url         (-> session-jane
                                    (request fetch-url
                                             :request-method :post)
                                    (ltu/body->edn)
                                    (ltu/is-status 202)
                                    (ltu/is-key-value #(-> % (str/split #" ") first) :message "starting")
                                    (ltu/location-url))
                get-context-url (-> session-admin
                                    (request job-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/is-operation-present :get-context)
                                    (ltu/get-op-url :get-context))]
            ;; since it's a fake deployment get-context will fail with not-found
            (-> session-admin
                (request get-context-url)
                (ltu/body->edn)
                (ltu/is-status 404)))

          (-> session-other
              (request fetch-url
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 403))

          ;; check the fetch action doesn't create additional job if another one is pending
          (-> session-jane
              (request fetch-url
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 202)
              (ltu/is-key-value #(-> % (str/split #" ") first) :message "existing")))

        (-> session-anon
            (request test-uri
                     :request-method :put
                     :body (json/write-str valid-entry))
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; search
        (-> session-admin
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; logs can be deleted by user
        (-> session-jane
            (request test-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; resource should be deleted
        (-> session-admin
            (request test-uri)
            (ltu/body->edn)
            (ltu/is-status 404)))
      )))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [resource-uri :options]
                            [resource-uri :post]])))
