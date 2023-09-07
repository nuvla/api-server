(ns sixsq.nuvla.server.resources.deployment-set-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.resource-creation :as resource-creation]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment :as deployment]
    [sixsq.nuvla.server.resources.deployment-set :as t]
    [sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [sixsq.nuvla.server.resources.deployment-set.utils :as dep-set-utils]
    [sixsq.nuvla.server.resources.job.utils :as job-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.resources.module.utils :as module-utils]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))
(def deployment-base-uri (str p/service-context deployment/resource-type))
(def module-base-uri (str p/service-context module-utils/resource-type))

(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")

(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))

(def dep-set-name "deployment set for testing")

(def dep-apps-sets [{:id      "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5",
                     :version 11,
                     :overwrites
                     [{:targets
                       ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
                        "credential/bc258c46-4771-45d3-9b38-97afdf185f44"],
                       :applications
                       [{:id      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
                         :version 1,
                         :environmental-variables
                         [{:name  "var_1_value",
                           :value "overwritten var1 overwritten in deployment set"}
                          {:name "var_2", :value "overwritten in deployment set"}]}]}
                      {}
                      {}]}])

(def valid-deployment-set {:name              dep-set-name,
                           :start             false,
                           :applications-sets dep-apps-sets})

(def u-applications-sets-v11
  {:description               "mutli",
   :path                      "khaled/app-sets-multi",
   :tags                      ["abc"],
   :content
   {:updated    "2023-02-28T12:40:32.887Z",
    :applications-sets
    [{:name "set-1",
      :applications
      [{:id "module/fcc71f74-1898-4e38-a284-5997141801a7", :version 0}
       {:id "module/770f5090-be33-42a3-b9fe-0de4622f12ea", :version 0}
       {:id "module/188555b1-2006-4766-b287-f60e5e908197", :version 0}
       {:id      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
        :version 1,
        :environmental-variables
        [{:name "var_1_value", :value "overwritten var1"}]}]}
     {:name        "set-2",
      :description "set 2 nginx pro only",
      :applications
      [{:id "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2", :version 5}]}
     {:name        "set-3",
      :description "k8s",
      :applications
      [{:id      "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e",
        :version 0}]}],
    :created    "2023-02-28T12:40:32.887Z",
    :author     "jane@example.com",
    :created-by "internal",
    :id         "module-applications-sets/4fef2c2c-ddf4-4305-987d-22116a35f9c7",
    :commit     "edit set-1 app 4 var1 overwrite"},
   :updated                   "2023-04-03T11:47:29.348Z",
   :name                      "app sets multi",
   :created                   "2023-01-05T12:59:14.080Z",
   :parent-path               "khaled",
   :data-accept-content-types [],
   :updated-by                "user/jane",
   :created-by                "user/jane",
   :id                        "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5",
   :resource-type             "module",
   :acl
   {:edit-data ["group/nuvla-admin"],
    :owners    ["user/jane"],
    :view-acl  ["group/dev" "group/nuvla-admin"],
    :delete    ["group/nuvla-admin"],
    :view-meta ["group/dev" "group/nuvla-admin"],
    :edit-acl  ["group/nuvla-admin"],
    :view-data ["group/dev" "group/nuvla-admin"],
    :manage    ["group/nuvla-admin"],
    :edit-meta ["group/nuvla-admin"]},
   :operations
   [{:rel "edit", :href "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5"}
    {:rel "delete", :href "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5"}
    {:rel  "publish",
     :href "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5/publish"}
    {:rel  "unpublish",
     :href "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5/unpublish"}
    {:rel  "deploy",
     :href "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5/deploy"}
    {:rel "delete-version",
     :href
     "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5/delete-version"}],
   :logo-url                  "https://icon-library.com/images/sed-02-512_42024.png",
   :versions
   [{:href
     "module-applications-sets/b8a215cf-2d8a-436c-a4b1-8ec822cf0924",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/4fef2c2c-ddf4-4305-987d-22116a35f9c7",
     :author    "jane@example.com",
     :commit    "edit set-1 app 4 var1 overwrite",
     :published true}],
   :published                 true,
   :subtype                   "applications_sets"})


(def app-set-dep-set {:targets
                      ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
                       "credential/bc258c46-4771-45d3-9b38-97afdf185f44"],
                      :applications
                      [{:id      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
                        :version 1,
                        :environmental-variables
                        [{:name  "var_1_value",
                          :value "overwritten var1 overwritten in deployment set"}
                         {:name "var_2", :value "overwritten in deployment set"}]}]})
(def u-deployment-set
  {:updated       "2023-04-12T12:35:00.564Z",
   :name          "New dep set",
   :start         false,
   :applications-sets
   [{:id      "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5",
     :version 11,
     :overwrites
     [app-set-dep-set
      {}
      {}]}],
   :created       "2023-04-12T12:35:00.564Z",
   :state         "CREATING",
   :created-by    "user/jane",
   :id            "deployment-set/51e193db-8c21-4bf2-a5b2-852f6c22e20d",
   :resource-type "deployment-set",
   :acl
   {:edit-data ["group/nuvla-admin"],
    :owners    ["user/jane"],
    :view-acl  ["group/nuvla-admin"],
    :delete    ["group/nuvla-admin"],
    :view-meta ["group/nuvla-admin"],
    :edit-acl  ["group/nuvla-admin"],
    :view-data ["group/nuvla-admin"],
    :manage    ["group/nuvla-admin"],
    :edit-meta ["group/nuvla-admin"]},
   :operations
   [{:rel  "edit",
     :href "deployment-set/51e193db-8c21-4bf2-a5b2-852f6c22e20d"}
    {:rel  "delete",
     :href "deployment-set/51e193db-8c21-4bf2-a5b2-852f6c22e20d"}
    {:rel "create",
     :href
     "deployment-set/51e193db-8c21-4bf2-a5b2-852f6c22e20d/create"}]})

(deftest plan-test
  (is (= (dep-set-utils/plan u-deployment-set u-applications-sets-v11)
         #{{:app-set     "set-1"
            :application {:environmental-variables [{:name  "var_1_value"
                                                     :value "overwritten var1 overwritten in deployment set"}
                                                    {:name  "var_2"
                                                     :value "overwritten in deployment set"}]
                          :id                      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"
                          :version                 1}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:environmental-variables [{:name  "var_1_value"
                                                     :value "overwritten var1 overwritten in deployment set"}
                                                    {:name  "var_2"
                                                     :value "overwritten in deployment set"}]
                          :id                      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"
                          :version                 1}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      "module/188555b1-2006-4766-b287-f60e5e908197"
                          :version 0}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      "module/188555b1-2006-4766-b287-f60e5e908197"
                          :version 0}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      "module/770f5090-be33-42a3-b9fe-0de4622f12ea"
                          :version 0}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      "module/770f5090-be33-42a3-b9fe-0de4622f12ea"
                          :version 0}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                          :version 0}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                          :version 0}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}})))

(deftest module-utils_get-applications-sets
  (is (= (module-utils/get-applications-sets u-applications-sets-v11)
         [{:applications [{:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                           :version 0}
                          {:id      "module/770f5090-be33-42a3-b9fe-0de4622f12ea"
                           :version 0}
                          {:id      "module/188555b1-2006-4766-b287-f60e5e908197"
                           :version 0}
                          {:environmental-variables [{:name  "var_1_value"
                                                      :value "overwritten var1"}]
                           :id                      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"
                           :version                 1}]
           :name         "set-1"}
          {:applications [{:id      "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2"
                           :version 5}]
           :description  "set 2 nginx pro only"
           :name         "set-2"}
          {:applications [{:id      "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e"
                           :version 0}]
           :description  "k8s"
           :name         "set-3"}])))

(deftest dep-set-utils_get-applications-sets
  (is (= (dep-set-utils/get-applications-sets u-deployment-set)
         [{:applications [{:environmental-variables [{:name  "var_1_value"
                                                      :value "overwritten var1 overwritten in deployment set"}
                                                     {:name  "var_2"
                                                      :value "overwritten in deployment set"}]
                           :id                      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"
                           :version                 1}]
           :targets      ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
                          "credential/bc258c46-4771-45d3-9b38-97afdf185f44"]}
          {}
          {}])))

(deftest dep-set-utils_get-targets
  (is (= (dep-set-utils/app-set-targets app-set-dep-set)
         ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
          "credential/bc258c46-4771-45d3-9b38-97afdf185f44"])))

(deftest dep-set-utils_get-applications
  (is (= (dep-set-utils/app-set-applications {}) []))
  (is (= (dep-set-utils/app-set-applications app-set-dep-set)
         [{:environmental-variables [{:name  "var_1_value"
                                      :value "overwritten var1 overwritten in deployment set"}
                                     {:name  "var_2"
                                      :value "overwritten in deployment set"}]
           :id                      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"
           :version                 1}])))

(deftest lifecycle
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
        session-user  (header session-anon authn-info-header
                              (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))]

    (testing "admin/user query succeeds but is empty"
      (doseq [session [session-admin session-user]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?)
            (ltu/is-operation-present :add)
            (ltu/is-operation-absent :delete)
            (ltu/is-operation-absent :edit))))

    (testing "anon query fails"
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "anon create must fail"
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "create must be possible for user"
      (let [{{{:keys [resource-id]} :body}
             :response} (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-deployment-set))
                            (ltu/body->edn)
                            (ltu/is-status 201))

            dep-set-url   (str p/service-context resource-id)
            authn-payload {"authn-info" {"active-claim" "user/jane"
                                         "claims"       ["group/nuvla-anon"
                                                         "user/jane"
                                                         "group/nuvla-user"
                                                         session-id]
                                         "user-id"      "user/jane"}}
            job-payload   (merge authn-payload
                                 {"filter" (str "deployment-set='" resource-id "'")})]

        (testing "user query should see one document"
          (-> session-user
              (request base-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-resource-uri t/collection-type)
              (ltu/is-count 1)))

        (testing "user retrieve should work and contain job"
          (-> session-user
              (request dep-set-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "NEW")
              (ltu/is-operation-present :start)
              (ltu/is-key-value :applications-sets dep-apps-sets)))

        #_(testing "user plan action should be built on demand"
            (with-redefs [crud/get-resource-throw-nok
                          (constantly u-applications-sets-v11)]
              (-> session-user
                  (request (-> session-user
                               (request dep-set-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/get-op-url :plan)))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/body)
                  set
                  (= #{{:target  "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                        :application
                        {:id "module/188555b1-2006-4766-b287-f60e5e908197", :version 0},
                        :app-set "set-1"}
                       {:target  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                        :application
                        {:id "module/188555b1-2006-4766-b287-f60e5e908197", :version 0},
                        :app-set "set-1"}
                       {:target  "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                        :application
                        {:id "module/770f5090-be33-42a3-b9fe-0de4622f12ea", :version 0},
                        :app-set "set-1"}
                       {:target  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                        :application
                        {:id "module/770f5090-be33-42a3-b9fe-0de4622f12ea", :version 0},
                        :app-set "set-1"}
                       {:target  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                        :application
                        {:id "module/fcc71f74-1898-4e38-a284-5997141801a7", :version 0},
                        :app-set "set-1"}
                       {:target  "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                        :application
                        {:id "module/fcc71f74-1898-4e38-a284-5997141801a7", :version 0},
                        :app-set "set-1"}
                       {:target  "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                        :application
                        {:id      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
                         :version 1,
                         :environmental-variables
                         [{:name  "var_1_value",
                           :value "overwritten var1 overwritten in deployment set"}
                          {:name "var_2", :value "overwritten in deployment set"}]},
                        :app-set "set-1"}
                       {:target  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                        :application
                        {:id      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
                         :version 1,
                         :environmental-variables
                         [{:name  "var_1_value",
                           :value "overwritten var1 overwritten in deployment set"}
                          {:name "var_2", :value "overwritten in deployment set"}]},
                        :app-set "set-1"}})
                  (is))))

        (testing "start action will create a start_deployment_set job"
          (let [start-op-url  (-> session-user
                                  (request dep-set-url)
                                  ltu/body->edn
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present :edit)
                                  (ltu/is-operation-present :delete)
                                  (ltu/is-operation-present :start)
                                  (ltu/is-operation-absent :stop)
                                  (ltu/is-operation-absent :update)
                                  (ltu/is-operation-absent :cancel)
                                  (ltu/get-op-url :start))
                start-job-url (-> session-user
                                  (request start-op-url)
                                  ltu/body->edn
                                  (ltu/is-status 202)
                                  ltu/location-url)]

            (-> session-user
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-operation-absent :edit)
                (ltu/is-operation-absent :delete)
                (ltu/is-operation-absent :start)
                (ltu/is-operation-absent :stop)
                (ltu/is-operation-absent :update)
                (ltu/is-operation-present :cancel)
                (ltu/is-key-value :state dep-set-utils/state-starting))

            (-> session-user
                (request start-job-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value :action "start_deployment_set")
                (ltu/is-key-value :href :target-resource resource-id))))

        (testing "force state transition to simulate job action"
          (-> session-admin
              (request (-> session-admin
                           (request dep-set-url)
                           ltu/body->edn
                           (ltu/is-status 200)
                           (ltu/is-operation-absent :edit)
                           (ltu/is-operation-absent :delete)
                           (ltu/is-operation-absent :start)
                           (ltu/is-operation-absent :stop)
                           (ltu/is-operation-absent :update)
                           (ltu/is-operation-present :cancel)
                           (ltu/is-operation-present :ok)
                           (ltu/is-operation-present :nok)
                           (ltu/get-op-url :ok)))
              ltu/body->edn
              (ltu/is-status 200))
          (-> session-user
              (request dep-set-url)
              ltu/body->edn
              (ltu/is-status 200)
              (ltu/is-operation-present :edit)
              (ltu/is-operation-absent :delete)
              (ltu/is-operation-absent :start)
              (ltu/is-operation-present :stop)
              (ltu/is-operation-present :update)
              (ltu/is-operation-absent :cancel)
              (ltu/is-key-value :state dep-set-utils/state-started)))

        (testing "edit action is possible"
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (json/write-str {:description "foo"}))
              ltu/body->edn
              (ltu/is-status 200)
              (ltu/is-key-value :description "foo")))

        (testing "update action will create a update_deployment_set job"
          (let [update-op-url (-> session-user
                                  (request dep-set-url)
                                  ltu/body->edn
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present :edit)
                                  (ltu/is-operation-absent :delete)
                                  (ltu/is-operation-absent :start)
                                  (ltu/is-operation-present :stop)
                                  (ltu/is-operation-present :update)
                                  (ltu/is-operation-absent :cancel)
                                  (ltu/get-op-url :update))
                job-url       (-> session-user
                                  (request update-op-url)
                                  ltu/body->edn
                                  (ltu/is-status 202)
                                  ltu/location-url)]
            (-> session-user
                (request job-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "update_deployment_set")
                (ltu/is-key-value json/read-str :payload job-payload))))

        (testing "edit action is not allowed in a transitional state"
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (json/write-str {:description "bar"}))
              ltu/body->edn
              (ltu/is-status 409)
              (ltu/message-matches "edit action is not allowed in state [UPDATING]")))

        (testing "force state transition to simulate job action"
          (-> session-admin
              (request (-> session-admin
                           (request dep-set-url)
                           ltu/body->edn
                           (ltu/is-status 200)
                           (ltu/is-operation-absent :edit)
                           (ltu/is-operation-absent :delete)
                           (ltu/is-operation-absent :start)
                           (ltu/is-operation-absent :stop)
                           (ltu/is-operation-absent :update)
                           (ltu/is-operation-present :cancel)
                           (ltu/is-operation-present :ok)
                           (ltu/is-operation-present :nok)
                           (ltu/get-op-url :ok)))
              ltu/body->edn
              (ltu/is-status 200))
          (-> session-user
              (request dep-set-url)
              ltu/body->edn
              (ltu/is-status 200)
              (ltu/is-operation-present :edit)
              (ltu/is-operation-absent :delete)
              (ltu/is-operation-absent :start)
              (ltu/is-operation-present :stop)
              (ltu/is-operation-present :update)
              (ltu/is-operation-absent :cancel)
              (ltu/is-key-value :state dep-set-utils/state-updated)))

        (testing "a second update action will create a new update_deployment_set job"
          (let [update-op-url (-> session-user
                                  (request dep-set-url)
                                  ltu/body->edn
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present :edit)
                                  (ltu/is-operation-absent :delete)
                                  (ltu/is-operation-absent :start)
                                  (ltu/is-operation-present :stop)
                                  (ltu/is-operation-present :update)
                                  (ltu/is-operation-absent :cancel)
                                  (ltu/get-op-url :update))
                job-url       (-> session-user
                                  (request update-op-url)
                                  ltu/body->edn
                                  (ltu/is-status 202)
                                  ltu/location-url)]
            (-> session-user
                (request job-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "update_deployment_set")
                (ltu/is-key-value json/read-str :payload job-payload))
            (testing "cancel action will cancel the running job"
              (let [cancel-op-url (-> session-user
                                      (request dep-set-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-operation-absent :edit)
                                      (ltu/is-operation-absent :delete)
                                      (ltu/is-operation-absent :start)
                                      (ltu/is-operation-absent :stop)
                                      (ltu/is-operation-absent :update)
                                      (ltu/is-operation-present :cancel)
                                      (ltu/get-op-url :cancel))]
                (-> session-user
                    (request cancel-op-url)
                    ltu/body->edn
                    (ltu/is-status 200))

                (-> session-user
                    (request job-url)
                    ltu/body->edn
                    (ltu/is-status 200)
                    (ltu/is-key-value :href :target-resource resource-id)
                    (ltu/is-key-value :action "update_deployment_set")
                    (ltu/is-key-value :state job-utils/state-canceled))

                (-> session-user
                    (request dep-set-url)
                    ltu/body->edn
                    (ltu/is-status 200)
                    (ltu/is-operation-present :edit)
                    (ltu/is-operation-absent :delete)
                    (ltu/is-operation-absent :start)
                    (ltu/is-operation-present :stop)
                    (ltu/is-operation-present :update)
                    (ltu/is-operation-absent :cancel)
                    (ltu/is-key-value :state dep-set-utils/state-partially-updated))
                ))))

        (testing "stop action will create a stop_deployment_set job"
          (let [stop-op-url (-> session-user
                                (request dep-set-url)
                                ltu/body->edn
                                (ltu/is-status 200)
                                (ltu/is-operation-present :edit)
                                (ltu/is-operation-absent :delete)
                                (ltu/is-operation-absent :start)
                                (ltu/is-operation-present :stop)
                                (ltu/is-operation-present :update)
                                (ltu/is-operation-absent :cancel)
                                (ltu/get-op-url :stop))
                job-url     (-> session-user
                                (request stop-op-url)
                                (ltu/body->edn)
                                (ltu/is-status 202)
                                (ltu/location-url))]
            (-> session-user
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "stop_deployment_set")
                (ltu/is-key-value json/read-str :payload job-payload))))))))

(deftest lifecycle-create-apps-sets
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header
                             (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
        module-id    (resource-creation/create-module session-user)
        fleet        ["nuvlabox/1"]]

    (module/initialize)

    (-> session-user
        (request (str p/service-context module-id)
                 :request-method :put
                 :body (json/write-str {:content {:docker-compose "a"
                                                  :author         "user/jane"}}))
        ltu/body->edn
        (ltu/is-status 200))

    (doseq [[expected-version m-id] [[1 module-id] [0 (str module-id "_0")]]]
      (let [dep-set-url (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str {:name    dep-set-name,
                                                            :start   false,
                                                            :modules [m-id]
                                                            :fleet   fleet}))
                            ltu/body->edn
                            (ltu/is-status 201)
                            ltu/location-url)
            app-set-id  (-> session-user
                            (request dep-set-url)
                            ltu/body->edn
                            (ltu/is-status 200)
                            (ltu/is-key-value
                              (comp :fleet first :overwrites first)
                              :applications-sets fleet)
                            ltu/body
                            :applications-sets
                            first
                            :id)
            app-set-url (str p/service-context app-set-id)]

        (testing
          "Application set was created in the right project
           and latest version of the application was picked up"
          (-> session-user
              (request app-set-url)
              ltu/body->edn
              (ltu/is-status 200)
              (ltu/is-key-value (comp first :applications first :applications-sets)
                                :content {:id      module-id
                                          :version expected-version})
              (ltu/is-key-value :parent-path module-utils/project-apps-sets)))))))

(deftest lifecycle-deployment-detach
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-user     (header session-anon authn-info-header
                                   (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
          {{{:keys [resource-id]} :body}
           :response} (-> session-user
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str valid-deployment-set))
                          (ltu/body->edn)
                          (ltu/is-status 201))

          dep-set-url      (str p/service-context resource-id)

          valid-deployment {:module         {:href "module/x"}
                            :deployment-set resource-id}
          dep-url          (with-redefs [module-utils/resolve-module (constantly {:href "module/x"})]
                             (-> session-user
                                 (request deployment-base-uri
                                          :request-method :post
                                          :body (json/write-str valid-deployment))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location-url)))
          detach-url       (-> session-user
                               (request dep-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-key-value :deployment-set resource-id)
                               (ltu/is-key-value :deployment-set-name dep-set-name)
                               (ltu/is-operation-present :detach)
                               (ltu/get-op-url :detach))
          new-dep-set-name "dep set name changed"]

      (testing "deployment set name is refreshed on edit of deployment"
        (-> session-user
            (request dep-set-url
                     :request-method :put
                     :body (json/write-str {:name new-dep-set-name}))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request dep-url
                     :request-method :put
                     :body (json/write-str {}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :deployment-set resource-id)
            (ltu/is-key-value :deployment-set-name new-dep-set-name)))

      (testing "user is able to detach deployment set"
        (-> session-user
            (request detach-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :deployment-set nil)
            (ltu/is-key-value :deployment-set-name nil)
            (ltu/is-operation-absent :detach))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
