(ns com.sixsq.nuvla.server.resources.deployment-set-lifecycle-test
  (:require
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.auth.utils :as auth]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.resource-creation :as resource-creation]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.deployment-set :as t]
    [com.sixsq.nuvla.server.resources.deployment-set.utils :as utils]
    [com.sixsq.nuvla.server.resources.infrastructure-service.utils :as infra-service-utils]
    [com.sixsq.nuvla.server.resources.job.utils :as job-utils]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.module.utils :as module-utils]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [com.sixsq.nuvla.server.util.time :as time]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))
(def deployment-base-uri (str p/service-context deployment/resource-type))

(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")

(defn state-transition
  [id action]
  (t/standard-action {:params      (assoc (u/id->request-params id)
                                     :action action)
                      :nuvla/authn auth/internal-identity}))

(deftest check-metadata
  (mdtu/check-metadata-exists t/resource-type))

(def dep-set-name "deployment set for testing")

(def app1-id "module/fcc71f74-1898-4e38-a284-5997141801a7")
(def app2-id "module/770f5090-be33-42a3-b9fe-0de4622f12ea")
(def app3-id "module/188555b1-2006-4766-b287-f60e5e908197")
(def app4-id "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8")
(def app5-id "module/ff0e0e39-4c22-411b-8c39-868aa50da1f5")
(def app6-id "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2")
(def app7-id "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e")

(def dep-apps-sets [{:id      app5-id,
                     :version 11,
                     :overwrites
                     [{:targets ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
                                 "credential/bc258c46-4771-45d3-9b38-97afdf185f44"]
                       :applications
                       [{:id      app4-id,
                         :version 1,
                         :environmental-variables
                         [{:name  "var_1_value",
                           :value "overwritten var1 overwritten in deployment set"}
                          {:name "var_2", :value "overwritten in deployment set"}]
                         :files
                         [{:file-name "file2", :file-content "overwritten in deployment set"}]}]}
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
      [{:id app1-id, :version 0}
       {:id app2-id, :version 0}
       {:id app3-id, :version 0}
       {:id      app4-id,
        :version 1,
        :environmental-variables
        [{:name "var_1_value", :value "overwritten var1"}]
        :files
        [{:file-name "file1", :file-content "overwritten file1 content"}]}]}
     {:name        "set-2",
      :description "set 2 nginx pro only",
      :applications
      [{:id app6-id, :version 5}]}
     {:name        "set-3",
      :description "k8s",
      :applications
      [{:id      app7-id,
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
   :id                        app5-id,
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
   [{:rel "edit", :href app5-id}
    {:rel "delete", :href app5-id}
    {:rel  "publish",
     :href (str app5-id "/publish")}
    {:rel  "unpublish",
     :href (str app5-id "/unpublish")}
    {:rel  "deploy",
     :href (str app5-id "/deploy")}
    {:rel  "delete-version",
     :href (str app5-id "/delete-version")}],
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


(def app-set-dep-set {:targets ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
                                "credential/bc258c46-4771-45d3-9b38-97afdf185f44"]
                      :applications
                      [{:id      app4-id,
                        :version 1,
                        :environmental-variables
                        [{:name  "var_1_value",
                          :value "overwritten var1 overwritten in deployment set"}
                         {:name "var_2", :value "overwritten in deployment set"}]
                        :files
                        [{:file-name "file2", :file-content "overwritten in deployment set"}]}]})
(def u-deployment-set
  {:updated       "2023-04-12T12:35:00.564Z",
   :name          "New dep set",
   :start         false,
   :applications-sets
   [{:id      app5-id,
     :version 11,
     :overwrites
     [app-set-dep-set
      {}
      {}]}],
   :created       "2023-04-12T12:35:00.564Z",
   :state         utils/state-new,
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
    :edit-meta ["group/nuvla-admin"]}})

(defn read-payload
  [payload]
  (-> payload
      j/read-value
      (update-in ["authn-info" "claims"] set)
      (update-in ["dg-authn-info" "claims"] set)
      (update-in ["dg-owner-authn-info" "claims"] set)))

(deftest plan-test
  (is (= (utils/plan u-deployment-set u-applications-sets-v11)
         #{{:app-set     "set-1"
            :application {:environmental-variables [{:name  "var_1_value"
                                                     :value "overwritten var1 overwritten in deployment set"}
                                                    {:name  "var_2"
                                                     :value "overwritten in deployment set"}]
                          :files                   [{:file-name "file1", :file-content "overwritten file1 content"}
                                                    {:file-content "overwritten in deployment set"
                                                     :file-name    "file2"}]
                          :id                      app4-id
                          :version                 1}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:environmental-variables [{:name  "var_1_value"
                                                     :value "overwritten var1 overwritten in deployment set"}
                                                    {:name  "var_2"
                                                     :value "overwritten in deployment set"}]
                          :files                   [{:file-name "file1", :file-content "overwritten file1 content"}
                                                    {:file-content "overwritten in deployment set"
                                                     :file-name    "file2"}]
                          :id                      app4-id
                          :version                 1}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      app3-id
                          :version 0}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      app3-id
                          :version 0}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      app2-id
                          :version 0}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      app2-id
                          :version 0}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      app1-id
                          :version 0}
            :target      "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      app1-id
                          :version 0}
            :target      "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}})))

(deftest module-utils_get-applications-sets
  (is (= (module-utils/get-applications-sets u-applications-sets-v11)
         [{:applications [{:id      app1-id
                           :version 0}
                          {:id      app2-id
                           :version 0}
                          {:id      app3-id
                           :version 0}
                          {:environmental-variables [{:name  "var_1_value"
                                                      :value "overwritten var1"}]
                           :files                   [{:file-name "file1", :file-content "overwritten file1 content"}]
                           :id                      app4-id
                           :version                 1}]
           :name         "set-1"}
          {:applications [{:id      app6-id
                           :version 5}]
           :description  "set 2 nginx pro only"
           :name         "set-2"}
          {:applications [{:id      app7-id
                           :version 0}]
           :description  "k8s"
           :name         "set-3"}])))

(deftest dep-set-utils_get-applications-sets
  (is (= (utils/get-applications-sets u-deployment-set)
         [{:applications [{:environmental-variables [{:name  "var_1_value"
                                                      :value "overwritten var1 overwritten in deployment set"}
                                                     {:name  "var_2"
                                                      :value "overwritten in deployment set"}]
                           :files                   [{:file-name "file2", :file-content "overwritten in deployment set"}]
                           :id                      app4-id
                           :version                 1}]
           :targets      ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
                          "credential/bc258c46-4771-45d3-9b38-97afdf185f44"]}
          {}
          {}])))

(deftest dep-set-utils_get-targets
  (is (= (utils/app-set-targets app-set-dep-set) ["credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"
                                                  "credential/bc258c46-4771-45d3-9b38-97afdf185f44"])))

(deftest dep-set-utils_get-applications
  (is (= (utils/app-set-applications {}) []))
  (is (= (utils/app-set-applications app-set-dep-set)
         [{:environmental-variables [{:name  "var_1_value"
                                      :value "overwritten var1 overwritten in deployment set"}
                                     {:name  "var_2"
                                      :value "overwritten in deployment set"}]
           :files                   [{:file-name "file2", :file-content "overwritten in deployment set"}]
           :id                      app4-id
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
            (ltu/is-operation-absent crud/action-delete)
            (ltu/is-operation-absent crud/action-edit))))

    (testing "anon query fails"
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "anon create must fail"
      (with-redefs [crud/get-resource-throw-nok
                    (constantly u-applications-sets-v11)]
        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (j/write-value-as-string {}))
            (ltu/body->edn)
            (ltu/is-status 403))))

    (testing "create must be possible for user"
      (let [{{{:keys [resource-id]} :body}
             :response} (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                          (-> session-user
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string valid-deployment-set))
                              (ltu/body->edn)
                              (ltu/is-status 201)))

            dep-set-url (str p/service-context resource-id)
            job-payload {"authn-info"          {"active-claim" "user/jane"
                                                "claims"       #{"group/nuvla-anon"
                                                                 "user/jane"
                                                                 "group/nuvla-user"
                                                                 session-id}
                                                "user-id"      "user/jane"}
                         "dg-authn-info"       {"active-claim" resource-id
                                                "claims"       #{resource-id
                                                                 "group/nuvla-user"
                                                                 "group/nuvla-anon"}
                                                "user-id"      resource-id}
                         "dg-owner-authn-info" {"active-claim" "user/jane"
                                                "claims"       #{"group/nuvla-anon"
                                                                 "user/jane"
                                                                 "group/nuvla-user"}
                                                "user-id"      "user/jane"}}]

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
              (ltu/is-key-value :state utils/state-new)
              (ltu/is-operation-present utils/action-start)
              (ltu/is-operation-present utils/action-plan)
              (ltu/is-operation-present utils/action-check-requirements)
              (ltu/is-operation-present utils/action-operational-status)
              (ltu/is-operation-present crud/action-delete)
              (ltu/is-operation-absent utils/action-force-delete)
              (ltu/is-key-value :applications-sets dep-apps-sets)
              (ltu/has-key :operational-status)
              (ltu/is-key-value :owner "user/jane")
              (ltu/is-key-value :api-endpoint "http://localhost")))

        (testing "acl check : DG id should be in edit-data/manage and the dg owner should be in acl edit-data/delete/manage"
          (-> session-admin
              (request dep-set-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :acl {:owners    ["group/nuvla-admin"]
                                      :edit-data [resource-id "user/jane"]
                                      :view-data [resource-id "user/jane"]
                                      :view-acl  [resource-id "user/jane"]
                                      :edit-meta [resource-id "user/jane"]
                                      :view-meta [resource-id "user/jane"]
                                      :manage    [resource-id "user/jane"]
                                      :delete    ["user/jane"]})))

        (testing "start action will create a bulk_deployment_set_start job"
          (let [start-op-url  (-> session-user
                                  (request dep-set-url)
                                  ltu/body->edn
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present crud/action-edit)
                                  (ltu/is-operation-present crud/action-delete)
                                  (ltu/is-operation-present utils/action-start)
                                  (ltu/is-operation-absent utils/action-stop)
                                  (ltu/is-operation-absent utils/action-update)
                                  (ltu/is-operation-absent utils/action-cancel)
                                  (ltu/is-operation-absent utils/action-recompute-fleet)
                                  (ltu/get-op-url utils/action-start))
                start-job-url (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                                (-> session-user
                                    (request start-op-url)
                                    ltu/body->edn
                                    (ltu/is-status 202)
                                    ltu/location-url))]

            (-> session-user
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-operation-absent crud/action-edit)
                (ltu/is-operation-absent crud/action-delete)
                (ltu/is-operation-absent utils/action-force-delete)
                (ltu/is-operation-absent utils/action-start)
                (ltu/is-operation-absent utils/action-stop)
                (ltu/is-operation-absent utils/action-update)
                (ltu/is-operation-present utils/action-cancel)
                (ltu/is-operation-absent utils/action-recompute-fleet)
                (ltu/is-key-value :state utils/state-starting))

            (-> session-user
                (request start-job-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value :action "bulk_deployment_set_start")
                (ltu/is-key-value :href :target-resource resource-id))))

        (testing "force state transition to simulate job action"
          (testing "admin should be able to edit in transitional state"
            (-> session-admin
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-operation-present crud/action-edit)
                (ltu/is-operation-absent crud/action-delete)
                (ltu/is-operation-absent utils/action-force-delete)
                (ltu/is-operation-absent utils/action-start)
                (ltu/is-operation-absent utils/action-stop)
                (ltu/is-operation-absent utils/action-update)
                (ltu/is-operation-present utils/action-cancel)
                (ltu/is-operation-absent utils/action-recompute-fleet)
                (ltu/is-key-value :state utils/state-starting))

            (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
              (-> session-admin
                  (request dep-set-url
                           :request-method :put
                           :body (j/write-value-as-string {:state utils/state-started}))
                  ltu/body->edn
                  (ltu/is-status 200)
                  (ltu/is-operation-present crud/action-edit)
                  (ltu/is-operation-absent crud/action-delete)
                  (ltu/is-operation-absent utils/action-start)
                  (ltu/is-operation-present utils/action-stop)
                  (ltu/is-operation-present utils/action-update)
                  (ltu/is-operation-absent utils/action-cancel)
                  (ltu/is-operation-absent utils/action-recompute-fleet)
                  (ltu/is-key-value :state utils/state-started)))))

        (testing "edit action is possible"
          (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
            (-> session-user
                (request dep-set-url
                         :request-method :put
                         :body (j/write-value-as-string {:description "foo"}))
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value :description "foo")
                (ltu/has-key :operational-status))))

        (testing "operational-status"
          (testing "deployment-set resource contains key operational-status")
          (let [os (-> session-user
                       (request dep-set-url)
                       (ltu/body->edn)
                       (ltu/is-status 200)
                       (ltu/has-key :operational-status)
                       (ltu/body)
                       :operational-status)]
            (is (= (:status os) "NOK"))
            (is (= (count (:deployments-to-add os)) 8))
            (is (= (count (:deployments-to-remove os)) 0))
            (is (= (count (:deployments-to-update os)) 0)))

          (testing "user should be able to call operational-status NOK"
            (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
              (-> session-user
                  (request (-> session-user
                               (request dep-set-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/get-op-url utils/action-operational-status)))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-key-value :status "NOK")
                  (ltu/is-key-value count :deployments-to-add 8)
                  (ltu/is-key-value count :deployments-to-remove 0)
                  (ltu/is-key-value count :deployments-to-update 0))))

          (let [plan             (utils/plan u-deployment-set u-applications-sets-v11)
                fake-deployment  (fn [{:keys [target] {app-id :id app-ver :version :keys [environmental-variables files]} :application}]
                                   {:id                  (str "deployment/" (u/rand-uuid))
                                    :parent              target
                                    :state               "STARTED"
                                    :module              {:content       {:id                      (str "module/" app-ver)
                                                                          :environmental-variables environmental-variables
                                                                          :files                   files}
                                                          :versions      (map (fn [i] {:href (str "module/" i)}) (range 3))
                                                          :published     true
                                                          :id            app-id
                                                          :href          app-id
                                                          :resource-type "module"
                                                          :subtype       "application"}
                                    :deployment-set      resource-id
                                    :deployment-set-name "Test Deployment Set"
                                    :app-set             "set-1"})
                fake-deployments (map fake-deployment plan)]

            (testing "user should be able to call operational-status"
              (with-redefs [utils/current-deployments
                            (constantly fake-deployments)
                            crud/get-resource-throw-nok
                            (constantly u-applications-sets-v11)]
                (-> session-user
                    (request (-> session-user
                                 (request dep-set-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/get-op-url utils/action-operational-status)))
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :status "OK")
                    (ltu/is-key-value :deployments-to-add nil)
                    (ltu/is-key-value :deployments-to-remove nil)
                    (ltu/is-key-value :deployments-to-update nil))))

            (testing "editing deployment set will update the operational status"
              (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                (-> session-user
                    (request dep-set-url
                             :request-method :put
                             :body (j/write-value-as-string {}))
                    ltu/body->edn
                    (ltu/is-status 200)
                    (ltu/is-key-value :state utils/state-started)
                    (ltu/is-operation-present utils/action-update))

                (with-redefs [utils/current-deployments
                              (constantly fake-deployments)]
                  (-> session-user
                      (request dep-set-url
                               :request-method :put
                               :body (j/write-value-as-string {}))
                      ltu/body->edn
                      (ltu/is-status 200)
                      (ltu/is-key-value :state utils/state-started)
                      (ltu/is-operation-absent utils/action-start)
                      (ltu/is-operation-absent utils/action-update)))))

            (testing "delete a deployment: user should be able to call operational-status and see a divergence"
              (with-redefs [utils/current-deployments   (constantly (drop 1 fake-deployments))
                            crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                (-> session-user
                    (request (-> session-user
                                 (request dep-set-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/get-op-url utils/action-operational-status)))
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :status "NOK")
                    (ltu/is-key-value :deployments-to-add [(first plan)]))

                ;; edit the deployment again to refresh the operational status
                (-> session-user
                    (request dep-set-url
                             :request-method :put
                             :body (j/write-value-as-string {}))
                    ltu/body->edn
                    (ltu/is-status 200)
                    (ltu/is-key-value :state utils/state-started)
                    (ltu/is-operation-present utils/action-update))))))


        (testing "update action will create a bulk_deployment_set_update job"
          (let [update-op-url (-> session-user
                                  (request dep-set-url)
                                  ltu/body->edn
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present crud/action-edit)
                                  (ltu/is-operation-absent crud/action-delete)
                                  (ltu/is-operation-absent utils/action-start)
                                  (ltu/is-operation-present utils/action-stop)
                                  (ltu/is-operation-present utils/action-update)
                                  (ltu/is-operation-absent utils/action-cancel)
                                  (ltu/is-operation-absent utils/action-recompute-fleet)
                                  (ltu/get-op-url utils/action-update))
                job-url       (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                                (-> session-user
                                    (request update-op-url)
                                    ltu/body->edn
                                    (ltu/is-status 202)
                                    ltu/location-url))]
            (-> session-user
                (request job-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "bulk_deployment_set_update")
                (ltu/is-key-value read-payload :payload job-payload))))

        (testing "edit action is not allowed in a transitional state"
          (with-redefs [crud/get-resource-throw-nok
                        (constantly u-applications-sets-v11)]
            (-> session-user
                (request dep-set-url
                         :request-method :put
                         :body (j/write-value-as-string {:description "bar"}))
                ltu/body->edn
                (ltu/is-status 409)
                (ltu/message-matches "edit action is not allowed in state [UPDATING]"))))

        (testing "force state transition to simulate job action"
          (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
            (state-transition resource-id utils/action-nok))
          (-> session-user
              (request dep-set-url)
              ltu/body->edn
              (ltu/is-status 200)
              (ltu/is-operation-present crud/action-edit)
              (ltu/is-operation-absent crud/action-delete)
              (ltu/is-operation-absent utils/action-start)
              (ltu/is-operation-present utils/action-stop)
              (ltu/is-operation-present utils/action-update)
              (ltu/is-operation-absent utils/action-cancel)
              (ltu/is-operation-absent utils/action-recompute-fleet)
              (ltu/is-key-value :state utils/state-partially-updated)))

        (testing "a second update action will create a new bulk_deployment_set_update job"
          (let [update-op-url (-> session-user
                                  (request dep-set-url)
                                  ltu/body->edn
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present crud/action-edit)
                                  (ltu/is-operation-absent crud/action-delete)
                                  (ltu/is-operation-absent utils/action-start)
                                  (ltu/is-operation-present utils/action-stop)
                                  (ltu/is-operation-present utils/action-update)
                                  (ltu/is-operation-absent utils/action-cancel)
                                  (ltu/is-operation-absent utils/action-recompute-fleet)
                                  (ltu/get-op-url utils/action-update))
                job-url       (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                                (-> session-user
                                    (request update-op-url)
                                    ltu/body->edn
                                    (ltu/is-status 202)
                                    ltu/location-url))]
            (-> session-user
                (request job-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "bulk_deployment_set_update")
                (ltu/is-key-value read-payload :payload job-payload))
            (testing "cancel action will cancel the running job"
              (let [cancel-op-url (-> session-user
                                      (request dep-set-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-operation-absent crud/action-edit)
                                      (ltu/is-operation-absent crud/action-delete)
                                      (ltu/is-operation-absent utils/action-start)
                                      (ltu/is-operation-absent utils/action-stop)
                                      (ltu/is-operation-absent utils/action-update)
                                      (ltu/is-operation-present utils/action-cancel)
                                      (ltu/is-operation-absent utils/action-recompute-fleet)
                                      (ltu/get-op-url utils/action-cancel))]
                (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                  (-> session-user
                      (request cancel-op-url)
                      ltu/body->edn
                      (ltu/is-status 200)))

                (-> session-user
                    (request job-url)
                    ltu/body->edn
                    (ltu/is-status 200)
                    (ltu/is-key-value :href :target-resource resource-id)
                    (ltu/is-key-value :action "bulk_deployment_set_update")
                    (ltu/is-key-value :state job-utils/state-canceled))

                (-> session-user
                    (request dep-set-url)
                    ltu/body->edn
                    (ltu/is-status 200)
                    (ltu/is-operation-present crud/action-edit)
                    (ltu/is-operation-absent crud/action-delete)
                    (ltu/is-operation-absent utils/action-start)
                    (ltu/is-operation-present utils/action-stop)
                    (ltu/is-operation-present utils/action-update)
                    (ltu/is-operation-absent utils/action-cancel)
                    (ltu/is-operation-absent utils/action-recompute-fleet)
                    (ltu/is-key-value :state utils/state-partially-updated))
                ))))

        (testing "stop action will create a bulk_deployment_set_stop job"
          (let [stop-op-url (-> session-user
                                (request dep-set-url)
                                ltu/body->edn
                                (ltu/is-status 200)
                                (ltu/is-operation-present crud/action-edit)
                                (ltu/is-operation-absent crud/action-delete)
                                (ltu/is-operation-absent utils/action-start)
                                (ltu/is-operation-present utils/action-stop)
                                (ltu/is-operation-present utils/action-update)
                                (ltu/is-operation-absent utils/action-cancel)
                                (ltu/is-operation-absent utils/action-recompute-fleet)
                                (ltu/get-op-url utils/action-stop))
                job-url     (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                              (-> session-user
                                  (request stop-op-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 202)
                                  (ltu/location-url)))]
            (-> session-user
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "bulk_deployment_set_stop")
                (ltu/is-key-value read-payload :payload job-payload))
            (-> session-user
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-operation-absent crud/action-edit)
                (ltu/is-operation-absent crud/action-delete)
                (ltu/is-operation-absent utils/action-start)
                (ltu/is-operation-absent utils/action-stop)
                (ltu/is-operation-present utils/action-cancel)
                (ltu/is-operation-absent utils/action-recompute-fleet)
                (ltu/is-key-value :state utils/state-stopping))))

        (testing "force state transition to simulate job action"
          (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
            (state-transition resource-id utils/action-nok))

          (-> session-user
              (request dep-set-url)
              ltu/body->edn
              (ltu/is-status 200)
              (ltu/is-operation-present crud/action-edit)
              (ltu/is-operation-absent crud/action-delete)
              (ltu/is-operation-present utils/action-start)
              (ltu/is-operation-present utils/action-stop)
              (ltu/is-operation-present utils/action-force-delete)
              (ltu/is-operation-absent utils/action-update)
              (ltu/is-operation-absent utils/action-cancel)
              (ltu/is-operation-absent utils/action-recompute-fleet)
              (ltu/is-key-value :state utils/state-partially-stopped)))

        (testing "force delete deployment set will create a job"
          (let [force-delete-op (-> session-user
                                    (request dep-set-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/get-op-url utils/action-force-delete))]
            (testing "on-done of job without success deployment-set is not deleted"
              (let [job-url (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                              (-> session-user
                                  (request force-delete-op)
                                  (ltu/body->edn)
                                  (ltu/is-status 202)
                                  ltu/location-url))]
                (-> session-admin
                    (request job-url
                             :request-method :put
                             :body (j/write-value-as-string {:state "FAILED"}))
                    (ltu/body->edn)
                    (ltu/is-status 200)))
              (-> session-user
                  (request dep-set-url)
                  (ltu/body->edn)
                  (ltu/is-status 200)))

            (testing "on-done of job with success deployment-set is deleted"
              (let [job-url (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                              (-> session-user
                                  (request force-delete-op)
                                  (ltu/body->edn)
                                  (ltu/is-status 202)
                                  ltu/location-url))]
                (-> session-admin
                    (request job-url
                             :request-method :put
                             :body (j/write-value-as-string {:state "SUCCESS"}))
                    (ltu/body->edn)
                    (ltu/is-status 200)))
              (-> session-user
                  (request dep-set-url)
                  (ltu/body->edn)
                  (ltu/is-status 404)))))))))

(deftest lifecycle-owner
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
        session-user  (header session-anon authn-info-header
                              (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
        module-id     (resource-creation/create-module session-user "p1" "p1/m1")
        ;; create a second module as admin
        module-id-2   (resource-creation/create-module session-admin "p2" "p2/m2")
        ne-id-1       (resource-creation/create-nuvlabox session-user {})
        ;; create a second nuvlaedge as admin
        ne-id-2       (resource-creation/create-nuvlabox session-admin {})
        fleet         [ne-id-1]
        fleet-filter  "resource-type='nuvlabox'"]

    (module/initialize)

    (-> session-user
        (request (str p/service-context module-id)
                 :request-method :put
                 :body (j/write-value-as-string {:content {:docker-compose "a"
                                                  :author         "user/jane"}}))
        ltu/body->edn
        (ltu/is-status 200))

    (-> session-admin
        (request (str p/service-context module-id-2)
                 :request-method :put
                 :body (j/write-value-as-string {:content {:docker-compose "a"
                                                  :author         "user/jane"}}))
        ltu/body->edn
        (ltu/is-status 200))

    (testing "Cannot add not accessible modules"
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string {:name    dep-set-name,
                                          :start   false,
                                          :modules [module-id module-id-2]
                                          :fleet   fleet}))
          ltu/body->edn
          (ltu/is-status 403)))

    (testing "Cannot add not accessible edges"
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string {:name    dep-set-name,
                                          :start   false,
                                          :modules [module-id]
                                          :fleet   [ne-id-1 ne-id-2]}))
          ltu/body->edn
          (ltu/is-status 403)))

    (let [dep-set-url      (-> session-user
                               (request base-uri
                                        :request-method :post
                                        :body (j/write-value-as-string {:name         dep-set-name,
                                                               :start        false,
                                                               :modules      [module-id]
                                                               :fleet        fleet
                                                               :fleet-filter fleet-filter}))
                               ltu/body->edn
                               (ltu/is-status 201)
                               ltu/location-url)
          dep-set          (-> session-user
                               (request dep-set-url)
                               ltu/body->edn
                               (ltu/is-status 200)
                               (ltu/is-key-value
                                 (comp :fleet first :overwrites first)
                                 :applications-sets fleet)
                               (ltu/is-key-value
                                 (comp :fleet-filter first :overwrites first)
                                 :applications-sets fleet-filter)
                               (ltu/is-operation-present utils/action-recompute-fleet)
                               ltu/body)
          dep-set-as-admin (-> session-admin
                               (request dep-set-url)
                               ltu/body->edn
                               (ltu/is-status 200)
                               (ltu/is-key-value
                                 (comp :fleet first :overwrites first)
                                 :applications-sets fleet)
                               (ltu/is-key-value
                                 (comp :fleet-filter first :overwrites first)
                                 :applications-sets fleet-filter)
                               (ltu/is-operation-present utils/action-recompute-fleet)
                               ltu/body)]
      (is (= dep-set dep-set-as-admin))

      (testing "Fleet filter"
        (-> session-user
            (request dep-set-url
                     :request-method :put
                     :body (j/write-value-as-string (assoc dep-set :modules [module-id]
                                                          :fleet-filter fleet-filter)))
            ltu/body->edn
            (ltu/is-status 200))
        (-> session-user
            (request dep-set-url)
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value
              (comp :fleet first :overwrites first)
              :applications-sets [ne-id-1])
            (ltu/is-key-value
              (comp :fleet-filter first :overwrites first)
              :applications-sets fleet-filter)
            (ltu/is-operation-present utils/action-recompute-fleet)))

      (testing "Fleet filter as admin"
        (-> session-admin
            (request dep-set-url
                     :request-method :put
                     :body (j/write-value-as-string (assoc dep-set :modules [module-id]
                                                          :fleet-filter fleet-filter)))
            ltu/body->edn
            (ltu/is-status 200))
        (-> session-admin
            (request dep-set-url)
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value
              (comp :fleet first :overwrites first)
              :applications-sets [ne-id-1])
            (ltu/is-key-value
              (comp :fleet-filter first :overwrites first)
              :applications-sets fleet-filter)
            (ltu/is-operation-present utils/action-recompute-fleet)))

      (testing "Recompute fleet as admin"
        (let [recompute-fleet-op (-> session-admin
                                     (request dep-set-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/get-op-url utils/action-recompute-fleet))]
          (-> session-admin
              (request recompute-fleet-op)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value
                (comp :fleet first :overwrites first)
                :applications-sets [ne-id-1])))))))

(deftest lifecycle-create-apps-sets
  (with-redefs [utils/get-missing-edges (constantly #{})]
    (let [session-anon (-> (ltu/ring-app)
                           session
                           (content-type "application/json"))
          session-user (header session-anon authn-info-header
                               (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
          module-id    (resource-creation/create-module session-user "p1" "p1/m1")
          module-id-2  (resource-creation/create-module session-user "p2" "p2/m2")
          ne-id-1      (resource-creation/create-nuvlabox session-user {})
          ne-id-2      (resource-creation/create-nuvlabox session-user {})
          fleet        [ne-id-1]
          fleet-filter "resource-type='nuvlabox'"]

      (module/initialize)

      (-> session-user
          (request (str p/service-context module-id)
                   :request-method :put
                   :body (j/write-value-as-string {:content {:docker-compose "a"
                                                    :author         "user/jane"}}))
          ltu/body->edn
          (ltu/is-status 200))

      (let [dep-set-url (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string {:name         dep-set-name,
                                                            :start        false,
                                                            :modules      [module-id]
                                                            :fleet        fleet
                                                            :fleet-filter fleet-filter}))
                            ltu/body->edn
                            (ltu/is-status 201)
                            ltu/location-url)
            dep-set     (-> session-user
                            (request dep-set-url)
                            ltu/body->edn
                            (ltu/is-status 200)
                            (ltu/is-key-value
                              (comp :fleet first :overwrites first)
                              :applications-sets fleet)
                            (ltu/is-key-value
                              (comp :fleet-filter first :overwrites first)
                              :applications-sets fleet-filter)
                            (ltu/is-operation-present utils/action-recompute-fleet)
                            ltu/body)
            app-set-id  (-> dep-set
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
                                          :version 1})
              (ltu/is-key-value :parent-path module-utils/project-apps-sets)))

        (testing
          "In edit call application set is replaced by a new one only when :modules key is specified"
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (j/write-value-as-string dep-set))
              ltu/body->edn
              (ltu/is-status 200))
          (let [new-app-set-id (-> session-user
                                   (request dep-set-url)
                                   ltu/body->edn
                                   (ltu/is-status 200)
                                   (ltu/is-key-value
                                     (comp :fleet first :overwrites first)
                                     :applications-sets fleet)
                                   (ltu/is-key-value
                                     (comp :fleet-filter first :overwrites first)
                                     :applications-sets fleet-filter)
                                   ltu/body
                                   :applications-sets
                                   first
                                   :id)]
            (is (= new-app-set-id app-set-id)))
          (let [app-overwrites [{:id                      module-id-2
                                 :version                 0
                                 :environmental-variables [{:name "var01" :value "value01"}]}]]
            (-> session-user
                (request dep-set-url
                         :request-method :put
                         :body (j/write-value-as-string (assoc dep-set :modules [module-id]
                                                              :overwrites app-overwrites
                                                              :fleet fleet
                                                              :fleet-filter fleet-filter)))
                ltu/body->edn
                (ltu/is-status 200))
            (let [dep-set        (-> session-user
                                     (request dep-set-url)
                                     ltu/body->edn
                                     (ltu/is-status 200)
                                     (ltu/is-key-value
                                       (comp :fleet first :overwrites first)
                                       :applications-sets fleet)
                                     (ltu/is-key-value
                                       (comp :fleet-filter first :overwrites first)
                                       :applications-sets fleet-filter)
                                     (ltu/is-operation-present utils/action-recompute-fleet)
                                     ltu/body)
                  new-app-set-id (-> dep-set
                                     :applications-sets
                                     first
                                     :id)]
              (is (not= new-app-set-id app-set-id))
              (is (= app-overwrites (-> dep-set :applications-sets first :overwrites first :applications))))))

        (testing "Fleet filter"
          (let [dynamic-fleet [ne-id-1 ne-id-2]]
            (-> session-user
                (request dep-set-url
                         :request-method :put
                         :body (j/write-value-as-string (assoc dep-set :modules [module-id]
                                                              :fleet-filter fleet-filter)))
                ltu/body->edn
                (ltu/is-status 200))
            (-> session-user
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value
                  (comp set :fleet first :overwrites first)
                  :applications-sets (set dynamic-fleet))
                (ltu/is-key-value
                  (comp :fleet-filter first :overwrites first)
                  :applications-sets fleet-filter)
                (ltu/is-operation-present utils/action-recompute-fleet))))

        (testing "Add an edge and recompute fleet"
          (let [ne-id-3            (resource-creation/create-nuvlabox session-user {})
                recompute-fleet-op (-> session-user
                                       (request dep-set-url)
                                       (ltu/body->edn)
                                       (ltu/is-status 200)
                                       (ltu/get-op-url utils/action-recompute-fleet))]
            (-> session-user
                (request recompute-fleet-op)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value
                  (comp set :fleet first :overwrites first)
                  :applications-sets #{ne-id-1 ne-id-2 ne-id-3}))))))))

(deftest compatibility
  (let [session-anon      (-> (ltu/ring-app)
                              session
                              (content-type "application/json"))
        session-user      (header session-anon authn-info-header
                                  (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
        compose-module-id (resource-creation/create-module session-user "p1" "p1/m1")
        helm-module-id-2  (with-redefs [infra-service-utils/missing-helm-repo-url? (constantly false)]
                            (resource-creation/create-module session-user "p2" "p2/m2" resource-creation/valid-helm-module))
        msg               (fn [module-ids]
                            (str "Some apps are not compatible with the DG subtype : " module-ids))]

    (module/initialize)

    (testing "Cannot add incompatible modules"
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string {:name    dep-set-name,
                                          :subtype "docker-compose"
                                          :start   false,
                                          :modules [helm-module-id-2]}))
          ltu/body->edn
          (ltu/is-status 400)
          (ltu/is-key-value :message (msg [helm-module-id-2])))

      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string {:name    dep-set-name,
                                          :subtype "kubernetes"
                                          :start   false,
                                          :modules [compose-module-id]}))
          ltu/body->edn
          (ltu/is-status 400)
          (ltu/is-key-value :message (msg [compose-module-id])))

      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string {:name    dep-set-name,
                                          :subtype "kubernetes"
                                          :start   false,
                                          :modules [compose-module-id helm-module-id-2]}))
          ltu/body->edn
          (ltu/is-status 400)
          (ltu/is-key-value :message (msg [compose-module-id]))))

    (testing "Compatible modules are accepted"
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string {:name    dep-set-name,
                                          :subtype "docker-compose"
                                          :start   false,
                                          :modules [compose-module-id]}))
          ltu/body->edn
          (ltu/is-status 201))

      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string {:name    dep-set-name,
                                          :subtype "kubernetes"
                                          :start   false,
                                          :modules [helm-module-id-2]}))
          ltu/body->edn
          (ltu/is-status 201)))))

(deftest lifecycle-missing-edges
  (let [session-anon    (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
        session-user    (header session-anon authn-info-header
                                (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
        module-id       (resource-creation/create-module session-user)
        ne-id           (resource-creation/create-nuvlabox session-user {})
        ne-id-not-exist (resource-creation/create-nuvlabox session-user {})
        fleet           [ne-id ne-id-not-exist]]

    (module/initialize)
    (let [dep-set-url (-> session-user
                          (request base-uri
                                   :request-method :post
                                   :body (j/write-value-as-string {:name    dep-set-name,
                                                          :start   false,
                                                          :modules [module-id]
                                                          :fleet   fleet}))
                          ltu/body->edn
                          (ltu/is-status 201)
                          ltu/location-url)]

      ;; delete one edge
      (-> session-user
          (request (ltu/href->url ne-id-not-exist)
                   :request-method :delete)
          (ltu/is-status 200))

      ;; update the DG (with no changes) such that the operational status is recomputed
      (-> session-user
          (request dep-set-url
                   :request-method :put
                   :body (j/write-value-as-string {}))
          ltu/body->edn
          (ltu/is-status 200)
          ltu/body)

      (testing "deployment created only on existing edges, a missing-edges entry is there"
        (-> session-user
            (request dep-set-url)
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value :operational-status
                              {:deployments-to-add
                               [{:target  ne-id,
                                 :application
                                 {:id module-id, :version 0},
                                 :app-set "Main"}],
                               :missing-edges [ne-id-not-exist],
                               :status        "NOK"}))))))

(deftest lifecycle-deployment-detach
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-user     (header session-anon authn-info-header
                                   (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
          dep-set-id       (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                             (-> session-user
                                 (request base-uri
                                          :request-method :post
                                          :body (j/write-value-as-string valid-deployment-set))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location)))

          valid-deployment {:module         {:href "module/x"}
                            :deployment-set dep-set-id}
          dep-url          (with-redefs [module-utils/resolve-module (constantly {:href "module/x"})]
                             (-> session-user
                                 (request deployment-base-uri
                                          :request-method :post
                                          :body (j/write-value-as-string valid-deployment))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location-url)))
          detach-url       (-> session-user
                               (request dep-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-key-value :deployment-set dep-set-id)
                               (ltu/is-key-value :deployment-set-name dep-set-name)
                               (ltu/is-operation-present :detach)
                               (ltu/get-op-url :detach))]

      (testing "user is able to detach deployment set"
        (-> session-user
            (request detach-url)
            (ltu/body->edn)
            (ltu/is-status 200))
        (-> session-user
            (request dep-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :deployment-set nil)
            (ltu/is-key-value :deployment-set-name nil)
            (ltu/is-operation-absent :detach))))))

(deftest lifecycle-deployment-set-rename
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session-anon     (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-user     (header session-anon authn-info-header
                                   (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
          dep-set-id       (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
                             (-> session-user
                                 (request base-uri
                                          :request-method :post
                                          :body (j/write-value-as-string valid-deployment-set))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location)))

          dep-set-url      (str p/service-context dep-set-id)

          valid-deployment {:module         {:href "module/x"}
                            :deployment-set dep-set-id}
          dep-url          (with-redefs [module-utils/resolve-module (constantly {:href "module/x"})]
                             (-> session-user
                                 (request deployment-base-uri
                                          :request-method :post
                                          :body (j/write-value-as-string valid-deployment))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location-url)))
          new-dep-set-name "dep set name changed"]

      (-> session-user
          (request dep-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :deployment-set dep-set-id)
          (ltu/is-key-value :deployment-set-name dep-set-name))

      (testing "deployment set name is updated on edit of deployment set name"
        (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (j/write-value-as-string {:name new-dep-set-name}))
              (ltu/body->edn)
              (ltu/is-status 200)))

        (-> session-user
            (request dep-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :deployment-set dep-set-id)
            (ltu/is-key-value :deployment-set-name new-dep-set-name))))))


(deftest lifecycle-cancel-actions
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))]

    (testing "Canceling start action"
      (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
        (let [{{{:keys [resource-id]} :body}
               :response} (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string valid-deployment-set))
                              (ltu/body->edn)
                              (ltu/is-status 201))
              dep-set-url          (str p/service-context resource-id)
              valid-deployment     {:module         {:href "module/x"}
                                    :deployment-set resource-id}
              _dep-url             (with-redefs [module-utils/resolve-module (constantly {:href "module/x"})]
                                     (-> session-admin
                                         (request deployment-base-uri
                                                  :request-method :post
                                                  :body (j/write-value-as-string valid-deployment))
                                         (ltu/body->edn)
                                         (ltu/is-status 201)
                                         (ltu/location-url)))
              start-op-url         (-> session-admin
                                       (request dep-set-url)
                                       ltu/body->edn
                                       (ltu/is-status 200)
                                       (ltu/is-operation-present utils/action-start)
                                       (ltu/get-op-url utils/action-start))
              start-job-url        (-> session-admin
                                       (request start-op-url)
                                       ltu/body->edn
                                       (ltu/is-status 202)
                                       ltu/location-url)
              cancel-start-job-url (-> session-admin
                                       (request start-job-url)
                                       ltu/body->edn
                                       (ltu/is-status 200)
                                       (ltu/is-key-value :state job-utils/state-queued)
                                       (ltu/is-key-value :action "bulk_deployment_set_start")
                                       (ltu/is-operation-present job-utils/action-cancel)
                                       (ltu/get-op-url job-utils/action-cancel))]
          (-> session-admin
              (request cancel-start-job-url)
              (ltu/body->edn)
              (ltu/is-status 200))
          (-> session-admin
              (request dep-set-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state utils/state-partially-started)))))
    (testing "Canceling stop action - not all deployments stopped"
      (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
        (let [{{{:keys [resource-id]} :body}
               :response} (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string valid-deployment-set))
                              (ltu/body->edn)
                              (ltu/is-status 201))
              dep-set-url         (str p/service-context resource-id)
              valid-deployment    {:module         {:href "module/x"}
                                   :deployment-set resource-id}
              _dep-url            (with-redefs [module-utils/resolve-module (constantly {:href "module/x"})]
                                    (-> session-admin
                                        (request deployment-base-uri
                                                 :request-method :post
                                                 :body (j/write-value-as-string valid-deployment))
                                        (ltu/body->edn)
                                        (ltu/is-status 201)
                                        (ltu/location-url)))
              start-op-url        (-> session-admin
                                      (request dep-set-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present utils/action-start)
                                      (ltu/get-op-url utils/action-start))
              _                   (-> session-admin
                                      (request start-op-url)
                                      ltu/body->edn
                                      (ltu/is-status 202)
                                      ltu/location-url)
              _                   (state-transition resource-id utils/action-ok)
              stop-op-url         (-> session-admin
                                      (request dep-set-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present utils/action-stop)
                                      (ltu/get-op-url utils/action-stop))
              stop-job-url        (-> session-admin
                                      (request stop-op-url)
                                      ltu/body->edn
                                      (ltu/is-status 202)
                                      ltu/location-url)
              cancel-stop-job-url (-> session-admin
                                      (request stop-job-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-key-value :state job-utils/state-queued)
                                      (ltu/is-key-value :action "bulk_deployment_set_stop")
                                      (ltu/is-operation-present job-utils/action-cancel)
                                      (ltu/get-op-url job-utils/action-cancel))]
          ;; cancel the stop_deployment job
          (-> session-admin
              (request cancel-stop-job-url)
              (ltu/body->edn)
              (ltu/is-status 200))
          ;; the deployment set should go in PARTIALLY_STOPPED state
          (-> session-admin
              (request dep-set-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state utils/state-partially-stopped)))))
    (testing "Canceling stop action - all deployments stopped"
      (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
        (let [{{{:keys [resource-id]} :body}
               :response} (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string valid-deployment-set))
                              (ltu/body->edn)
                              (ltu/is-status 201))
              dep-set-url         (str p/service-context resource-id)
              valid-deployment    {:module         {:href "module/x"}
                                   :deployment-set resource-id}
              dep-url             (with-redefs [module-utils/resolve-module (constantly {:href "module/x"})]
                                    (-> session-admin
                                        (request deployment-base-uri
                                                 :request-method :post
                                                 :body (j/write-value-as-string valid-deployment))
                                        (ltu/body->edn)
                                        (ltu/is-status 201)
                                        (ltu/location-url)))
              ;; force deployment status to STOPPED
              _                   (-> session-admin
                                      (request dep-url
                                               :request-method :put
                                               :body (j/write-value-as-string {:state "STOPPED"}))
                                      (ltu/body->edn)
                                      (ltu/is-status 200))
              start-op-url        (-> session-admin
                                      (request dep-set-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present utils/action-start)
                                      (ltu/get-op-url utils/action-start))
              _                   (-> session-admin
                                      (request start-op-url)
                                      ltu/body->edn
                                      (ltu/is-status 202)
                                      ltu/location-url)
              _                   (state-transition resource-id utils/action-ok)
              stop-op-url         (-> session-admin
                                      (request dep-set-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present utils/action-stop)
                                      (ltu/get-op-url utils/action-stop))
              stop-job-url        (-> session-admin
                                      (request stop-op-url)
                                      ltu/body->edn
                                      (ltu/is-status 202)
                                      ltu/location-url)
              cancel-stop-job-url (-> session-admin
                                      (request stop-job-url)
                                      ltu/body->edn
                                      (ltu/is-status 200)
                                      (ltu/is-key-value :state job-utils/state-queued)
                                      (ltu/is-key-value :action "bulk_deployment_set_stop")
                                      (ltu/is-operation-present job-utils/action-cancel)
                                      (ltu/get-op-url job-utils/action-cancel))]
          ;; cancel the stop_deployment job
          (-> session-admin
              (request cancel-stop-job-url)
              (ltu/body->edn)
              (ltu/is-status 200))
          ;; the deployment set should go in PARTIALLY_STOPPED state
          (-> session-admin
              (request dep-set-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state utils/state-stopped)))))
    (testing "Canceling update action"
      (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
        (let [{{{:keys [resource-id]} :body}
               :response} (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string valid-deployment-set))
                              (ltu/body->edn)
                              (ltu/is-status 201))
              dep-set-url           (str p/service-context resource-id)
              valid-deployment      {:module         {:href "module/x"}
                                     :deployment-set resource-id}
              _dep-url              (with-redefs [module-utils/resolve-module (constantly {:href "module/x"})]
                                      (-> session-admin
                                          (request deployment-base-uri
                                                   :request-method :post
                                                   :body (j/write-value-as-string valid-deployment))
                                          (ltu/body->edn)
                                          (ltu/is-status 201)
                                          (ltu/location-url)))
              start-op-url          (-> session-admin
                                        (request dep-set-url)
                                        ltu/body->edn
                                        (ltu/is-status 200)
                                        (ltu/is-operation-present utils/action-start)
                                        (ltu/get-op-url utils/action-start))
              _                     (-> session-admin
                                        (request start-op-url)
                                        ltu/body->edn
                                        (ltu/is-status 202)
                                        ltu/location-url)
              _                     (state-transition resource-id utils/action-ok)
              update-op-url         (-> session-admin
                                        (request dep-set-url)
                                        ltu/body->edn
                                        (ltu/is-status 200)
                                        (ltu/is-operation-present utils/action-update)
                                        (ltu/get-op-url utils/action-update))
              update-job-url        (-> session-admin
                                        (request update-op-url)
                                        ltu/body->edn
                                        (ltu/is-status 202)
                                        ltu/location-url)
              cancel-update-job-url (-> session-admin
                                        (request update-job-url)
                                        ltu/body->edn
                                        (ltu/is-status 200)
                                        (ltu/is-key-value :state job-utils/state-queued)
                                        (ltu/is-key-value :action "bulk_deployment_set_update")
                                        (ltu/is-operation-present job-utils/action-cancel)
                                        (ltu/get-op-url job-utils/action-cancel))]
          (testing "cancel the update_deployment job"
            (-> session-admin
                (request cancel-update-job-url)
                (ltu/body->edn)
                (ltu/is-status 200)))

          (testing "the deployment set should go in PARTIALLY_UPDATED state"
            (-> session-admin
                (request dep-set-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :state utils/state-partially-updated))))))
    (testing "Canceling start action but job not found for some reason"
      (with-redefs [crud/get-resource-throw-nok (constantly u-applications-sets-v11)]
        (let [{{{:keys [resource-id]} :body}
               :response} (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (j/write-value-as-string valid-deployment-set))
                              (ltu/body->edn)
                              (ltu/is-status 201))
              dep-set-url   (str p/service-context resource-id)
              start-op-url  (-> session-admin
                                (request dep-set-url)
                                ltu/body->edn
                                (ltu/is-status 200)
                                (ltu/is-operation-present utils/action-start)
                                (ltu/get-op-url utils/action-start))
              job-url       (-> session-admin
                                (request start-op-url)
                                ltu/body->edn
                                (ltu/is-status 202)
                                ltu/location-url)
              cancel-op-url (-> session-admin
                                (request dep-set-url)
                                ltu/body->edn
                                (ltu/is-status 200)
                                (ltu/is-operation-present utils/action-cancel)
                                (ltu/get-op-url utils/action-cancel))]

          (-> session-admin
              (request job-url :request-method :delete)
              ltu/body->edn
              (ltu/is-status 200))
          (-> session-admin
              (request cancel-op-url)
              ltu/body->edn
              (ltu/is-status 404)
              (ltu/message-matches "no running operation found that can be cancelled")))))))


(deftest lifecycle-check-requirements
  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header
                             (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))]

    (let [module-id   (resource-creation/create-module session-user "a" "a/b")
          _           (module/initialize)
          module-id-2 (resource-creation/create-module session-user "c" "c/d")
          ne-id-1     (resource-creation/create-nuvlabox session-user {})
          fleet       [ne-id-1]]

      (testing "check deployment set requirements"
        (let [check-req-results (fn [module1-req module2-req status]
                                  (-> session-user
                                      (request (str p/service-context module-id)
                                               :request-method :put
                                               :body (j/write-value-as-string {:content (merge {:docker-compose "a"
                                                                                       :author         "user/jane"}
                                                                                      module1-req)}))
                                      ltu/body->edn
                                      (ltu/is-status 200))
                                  (-> session-user
                                      (request (str p/service-context module-id-2)
                                               :request-method :put
                                               :body (j/write-value-as-string {:content (merge {:docker-compose "a"
                                                                                       :author         "user/jane"}
                                                                                      module2-req)}))
                                      ltu/body->edn
                                      (ltu/is-status 200))
                                  (let [dep-set-url               (with-redefs [utils/get-missing-edges (constantly #{})]
                                                                    (-> session-user
                                                                        (request base-uri
                                                                                 :request-method :post
                                                                                 :body (j/write-value-as-string {:name    dep-set-name
                                                                                                        :modules [module-id module-id-2]
                                                                                                        :fleet   fleet}))
                                                                        ltu/body->edn
                                                                        (ltu/is-status 201)
                                                                        ltu/location-url))
                                        check-requirements-op-url (-> session-user
                                                                      (request dep-set-url)
                                                                      ltu/body->edn
                                                                      (ltu/is-status 200)
                                                                      (ltu/get-op-url utils/action-check-requirements))]
                                    (with-redefs [crud/query-as-admin
                                                  (fn [collection-id _options]
                                                    (case collection-id
                                                      "nuvlabox"
                                                      [{:count 1}
                                                       '({:id              "nuvlabox/1"
                                                          :name            "NuvlaBox 01"
                                                          :nuvlabox-status "nuvlabox-status/1"})]
                                                      "nuvlabox-status"
                                                      [{:count 1}
                                                       [(merge {:id "nuvlabox-status/1"} status)]]))]
                                      (-> session-user
                                          (request check-requirements-op-url)
                                          ltu/body->edn
                                          (ltu/is-status 200)
                                          ltu/body))))]
          (is (= {:minimum-requirements {:architectures ["x86_64"]
                                         :min-cpu       2.5
                                         :min-ram       300
                                         :min-disk      110}
                  :unmet-requirements   {:first-mismatches []
                                         :n-edges          0}}
                 (check-req-results {:architectures        ["x86_64"]
                                     :minimum-requirements {:min-cpu  2.0
                                                            :min-ram  200
                                                            :min-disk 10}}
                                    {:architectures        ["x86_64" "sparc"]
                                     :minimum-requirements {:min-cpu  0.5
                                                            :min-ram  100
                                                            :min-disk 100}}
                                    {:architecture "x86_64"
                                     :resources    {:cpu   {:capacity 3}
                                                    :ram   {:capacity 1024}
                                                    :disks [{:capacity 200}]}})))
          (is (= {:minimum-requirements {:architectures ["x86_64"]
                                         :min-cpu       2.5
                                         :min-ram       300
                                         :min-disk      110}
                  :unmet-requirements   {:first-mismatches [{:edge-id   "nuvlabox/1"
                                                             :edge-name "NuvlaBox 01"
                                                             :cpu       {:available 2
                                                                         :min       2.5}
                                                             :disk      {:available 20
                                                                         :min       110}}]
                                         :n-edges          1}}
                 (check-req-results {:architectures        ["x86_64"]
                                     :minimum-requirements {:min-cpu  2.0
                                                            :min-ram  200
                                                            :min-disk 10}}
                                    {:architectures        ["x86_64" "sparc"]
                                     :minimum-requirements {:min-cpu  0.5
                                                            :min-ram  100
                                                            :min-disk 100}}
                                    {:architecture "x86_64"
                                     :resources    {:cpu   {:capacity 2}
                                                    :ram   {:capacity 1024}
                                                    :disks [{:capacity 200 :used 180}]}})))
          (is (= {:minimum-requirements {:architectures ["x86_64"]
                                         :min-cpu       2.5
                                         :min-ram       300
                                         :min-disk      110}
                  :unmet-requirements   {:first-mismatches []
                                         :n-edges          0}}
                 (check-req-results {:architectures        ["x86_64"]
                                     :minimum-requirements {:min-cpu  2.0
                                                            :min-ram  200
                                                            :min-disk 10}}
                                    {:architectures        ["x86_64" "sparc"]
                                     :minimum-requirements {:min-cpu  0.5
                                                            :min-ram  100
                                                            :min-disk 100}}
                                    {:architecture "x86_64"
                                     :resources    {:cpu   {:capacity 3}
                                                    :ram   {:capacity 1024}
                                                    :disks [{:capacity 20}
                                                            {:capacity 200 :used 20}
                                                            {:capacity 40}]}})))
          (is (= {:minimum-requirements {:architectures ["x86_64"]
                                         :min-cpu       2.5
                                         :min-ram       300
                                         :min-disk      110}
                  :unmet-requirements   {:first-mismatches [{:edge-id      "nuvlabox/1"
                                                             :edge-name    "NuvlaBox 01"
                                                             :architecture {:edge-architecture "sparc"
                                                                            :supported         ["x86_64"]}}]
                                         :n-edges          1}}
                 (check-req-results {:architectures        ["x86_64"]
                                     :minimum-requirements {:min-cpu  2.0
                                                            :min-ram  200
                                                            :min-disk 10}}
                                    {:architectures        ["x86_64" "sparc"]
                                     :minimum-requirements {:min-cpu  0.5
                                                            :min-ram  100
                                                            :min-disk 100}}
                                    {:architecture "sparc"
                                     :resources    {:cpu   {:capacity 3}
                                                    :ram   {:capacity 1024}
                                                    :disks [{:capacity 200}]}})))
          (testing "The check should pass if required architecture is not specified"
            (is (= {:minimum-requirements {:architectures nil
                                           :min-cpu       2.5
                                           :min-ram       300
                                           :min-disk      110}
                    :unmet-requirements   {:first-mismatches []
                                           :n-edges          0}}
                   (check-req-results {:minimum-requirements {:min-cpu  2.0
                                                              :min-ram  200
                                                              :min-disk 10}}
                                      {:minimum-requirements {:min-cpu  0.5
                                                              :min-ram  100
                                                              :min-disk 100}}
                                      {:architecture ["x86_64"]
                                       :resources    {:cpu   {:capacity 3}
                                                      :ram   {:capacity 1024}
                                                      :disks [{:capacity 200}]}}))))
          (testing "The check should pass if required cpu is not specified"
            (is (= {:minimum-requirements {:architectures ["x86_64"]
                                           :min-cpu       0.0
                                           :min-ram       300
                                           :min-disk      110}
                    :unmet-requirements   {:first-mismatches []
                                           :n-edges          0}}
                   (check-req-results {:architectures        ["x86_64"]
                                       :minimum-requirements {:min-ram  200
                                                              :min-disk 10}}
                                      {:architectures        ["x86_64" "sparc"]
                                       :minimum-requirements {:min-ram  100
                                                              :min-disk 100}}
                                      {:architecture "x86_64"
                                       :resources    {:cpu   {:capacity 1}
                                                      :ram   {:capacity 1024}
                                                      :disks [{:capacity 200}]}}))))
          (testing "The check should pass if required ram is not specified"
            (is (= {:minimum-requirements {:architectures ["x86_64"]
                                           :min-cpu       2.5
                                           :min-ram       0
                                           :min-disk      110}
                    :unmet-requirements   {:first-mismatches []
                                           :n-edges          0}}
                   (check-req-results {:architectures        ["x86_64"]
                                       :minimum-requirements {:min-cpu  2.0
                                                              :min-disk 10}}
                                      {:architectures        ["x86_64" "sparc"]
                                       :minimum-requirements {:min-cpu  0.5
                                                              :min-disk 100}}
                                      {:architecture "x86_64"
                                       :resources    {:cpu   {:capacity 3}
                                                      :ram   {:capacity 40}
                                                      :disks [{:capacity 200}]}}))))
          (testing "The check should pass if required disk space is not specified"
            (is (= {:minimum-requirements {:architectures ["x86_64"]
                                           :min-cpu       2.5
                                           :min-ram       300
                                           :min-disk      0}
                    :unmet-requirements   {:first-mismatches []
                                           :n-edges          0}}
                   (check-req-results {:architectures        ["x86_64"]
                                       :minimum-requirements {:min-cpu 2.0
                                                              :min-ram 200}}
                                      {:architectures        ["x86_64" "sparc"]
                                       :minimum-requirements {:min-cpu 0.5
                                                              :min-ram 100}}
                                      {:architecture "x86_64"
                                       :resources    {:cpu   {:capacity 3}
                                                      :ram   {:capacity 1024}
                                                      :disks [{:capacity 10}]}}))))
          (testing "The check should pass if the edge does not provide supported architecture"
            (is (= {:minimum-requirements {:architectures ["x86_64"]
                                           :min-cpu       2.5
                                           :min-ram       300
                                           :min-disk      110}
                    :unmet-requirements   {:first-mismatches []
                                           :n-edges          0}}
                   (check-req-results {:architectures        ["x86_64"]
                                       :minimum-requirements {:min-cpu  2.0
                                                              :min-ram  200
                                                              :min-disk 10}}
                                      {:architectures        ["x86_64" "sparc"]
                                       :minimum-requirements {:min-cpu  0.5
                                                              :min-ram  100
                                                              :min-disk 100}}
                                      {:architecture nil
                                       :resources    {:cpu   {:capacity 3}
                                                      :ram   {:capacity 1024}
                                                      :disks [{:capacity 200}]}}))))
          (testing "The check should NOT pass if the edge does not provide available resources"
            (is (= {:minimum-requirements {:architectures ["x86_64"]
                                           :min-cpu       2.5
                                           :min-ram       300
                                           :min-disk      110}
                    :unmet-requirements   {:first-mismatches [{:cpu       {:available 0
                                                                           :min       2.5}
                                                               :edge-id   "nuvlabox/1"
                                                               :edge-name "NuvlaBox 01"}]
                                           :n-edges          1}}
                   (check-req-results {:architectures        ["x86_64"]
                                       :minimum-requirements {:min-cpu  2.0
                                                              :min-ram  200
                                                              :min-disk 10}}
                                      {:architectures        ["x86_64" "sparc"]
                                       :minimum-requirements {:min-cpu  0.5
                                                              :min-ram  100
                                                              :min-disk 100}}
                                      {:architecture "x86_64"
                                       :resources    {:ram   {:capacity 1024}
                                                      :disks [{:capacity 200}]}})))
            (is (= {:minimum-requirements {:architectures ["x86_64"]
                                           :min-cpu       2.5
                                           :min-ram       300
                                           :min-disk      110}
                    :unmet-requirements   {:first-mismatches [{:edge-id   "nuvlabox/1"
                                                               :edge-name "NuvlaBox 01"
                                                               :ram       {:available 0
                                                                           :min       300}}]
                                           :n-edges          1}}
                   (check-req-results {:architectures        ["x86_64"]
                                       :minimum-requirements {:min-cpu  2.0
                                                              :min-ram  200
                                                              :min-disk 10}}
                                      {:architectures        ["x86_64" "sparc"]
                                       :minimum-requirements {:min-cpu  0.5
                                                              :min-ram  100
                                                              :min-disk 100}}
                                      {:architecture "x86_64"
                                       :resources    {:cpu   {:capacity 3}
                                                      :disks [{:capacity 200}]}})))
            (is (= {:minimum-requirements {:architectures ["x86_64"]
                                           :min-cpu       2.5
                                           :min-ram       300
                                           :min-disk      110}
                    :unmet-requirements   {:first-mismatches [{:disk      {:available 0
                                                                           :min       110}
                                                               :edge-id   "nuvlabox/1"
                                                               :edge-name "NuvlaBox 01"}]
                                           :n-edges          1}}
                   (check-req-results {:architectures        ["x86_64"]
                                       :minimum-requirements {:min-cpu  2.0
                                                              :min-ram  200
                                                              :min-disk 10}}
                                      {:architectures        ["x86_64" "sparc"]
                                       :minimum-requirements {:min-cpu  0.5
                                                              :min-ram  100
                                                              :min-disk 100}}
                                      {:architecture "x86_64"
                                       :resources    {:cpu {:capacity 3}
                                                      :ram {:capacity 1024}}})))))))))

(deftest lifecycle-auto-update
  (let [session-anon  (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
        session-user  (header session-anon authn-info-header
                              (str "user/jane user/jane group/nuvla-user group/nuvla-anon " session-id))
        module-id     (resource-creation/create-module session-user)
        ne-id-1       (resource-creation/create-nuvlabox session-user {})
        fleet         [ne-id-1]]

    (module/initialize)

    (-> session-user
        (request (str p/service-context module-id)
                 :request-method :put
                 :body (j/write-value-as-string {:content {:docker-compose "a"
                                                  :author         "user/jane"}}))
        ltu/body->edn
        (ltu/is-status 200))

    (let [response    (-> session-user
                          (request base-uri
                                   :request-method :post
                                   :body (j/write-value-as-string {:name        dep-set-name,
                                                          :start       false,
                                                          :modules     [module-id]
                                                          :fleet       fleet
                                                          :auto-update true}))
                          ltu/body->edn
                          (ltu/is-status 201))
          dep-set-id  (ltu/location response)
          dep-set-url (ltu/location-url response)]

      (testing "Check that next-refresh is set to now + 1 minute."
        (-> session-user
            (request dep-set-url)
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value :auto-update true)
            (ltu/is-key-value
              (comp #(time/time-between (time/now) % :seconds) time/parse-date)
              :next-refresh 299)))

      (testing "Check that updating auto-update-interval also updates next-refresh."
        (-> session-user
            (request dep-set-url
                     :request-method :put
                     :body (j/write-value-as-string {:auto-update-interval 10}))
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value
              (comp #(time/time-between (time/now) % :seconds) time/parse-date)
              :next-refresh 599)))

      (testing "auto-update action not available in NEW state"
        (-> session-admin
            (request dep-set-url)
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value :state utils/state-new)
            (ltu/is-operation-absent utils/action-auto-update)))

      (-> session-user
          (request (-> session-user
                       (request dep-set-url)
                       ltu/body->edn
                       (ltu/is-status 200)
                       (ltu/is-operation-present utils/action-start)
                       (ltu/get-op-url utils/action-start)))
          ltu/body->edn
          (ltu/is-status 202))

      (state-transition dep-set-id utils/action-ok)

      (testing "auto-update action not available to non-admin users."
        (-> session-user
            (request dep-set-url)
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-key-value :state utils/state-started)
            (ltu/is-operation-absent utils/action-auto-update)))

      (testing "auto-update action available to admin user."
        (-> session-admin
            (request dep-set-url
                     :request-method :put
                     :body (j/write-value-as-string {:state utils/state-started}))
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-operation-present utils/action-auto-update)
            (ltu/is-key-value :state utils/state-started)))

      (testing "auto-update action not available when auto-update is not enabled"
        (-> session-admin
            (request dep-set-url
                     :request-method :put
                     :body (j/write-value-as-string {:auto-update false}))
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-operation-absent utils/action-auto-update))

        (-> session-admin
            (request dep-set-url
                     :request-method :put
                     :body (j/write-value-as-string {:auto-update true}))
            ltu/body->edn
            (ltu/is-status 200)
            (ltu/is-operation-present utils/action-auto-update)))

      (testing "Call auto-update action"
        (let [auto-update-op-url (-> session-admin
                                     (request dep-set-url)
                                     ltu/body->edn
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present utils/action-auto-update)
                                     (ltu/get-op-url utils/action-auto-update))]

          (testing "Static fleet"
            (-> session-admin
                (request auto-update-op-url)
                ltu/body->edn
                (ltu/is-status 202))

            (-> session-admin
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value (comp count :deployments-to-add) :operational-status 1)
                (ltu/is-key-value :state utils/state-updating))

            (state-transition dep-set-id utils/action-ok)

            (testing "Add a new edge, auto update and check that the fleet is updated in the operational status"
              (let [ne-id-2   (resource-creation/create-nuvlabox session-user {})
                    new-fleet [ne-id-1 ne-id-2]]
                (-> session-admin
                    (request dep-set-url
                             :request-method :put
                             :body (-> session-user
                                       (request dep-set-url)
                                       ltu/body->edn
                                       (ltu/is-status 200)
                                       ltu/body
                                       (assoc-in [:applications-sets 0 :overwrites 0 :fleet] new-fleet)
                                       j/write-value-as-string))
                    ltu/body->edn
                    (ltu/is-status 200))

                (-> session-admin
                    (request auto-update-op-url)
                    ltu/body->edn
                    (ltu/is-status 202))

                (-> session-user
                    (request dep-set-url)
                    ltu/body->edn
                    (ltu/is-status 200)
                    (ltu/is-key-value (comp count :deployments-to-add) :operational-status 2)
                    (ltu/is-key-value :state utils/state-updating)))))

          (state-transition dep-set-id utils/action-ok)

          (testing "Dynamic fleet"
            (-> session-user
                (request dep-set-url
                         :request-method :put
                         :body (-> session-user
                                   (request dep-set-url)
                                   ltu/body->edn
                                   (ltu/is-status 200)
                                   ltu/body
                                   (assoc-in [:applications-sets 0 :overwrites 0 :fleet-filter] "resource:type='nuvlabox'")
                                   j/write-value-as-string))
                ltu/body->edn
                (ltu/is-status 200))

            (testing "with recompute fleet 0 nuvlaedge match filter"
              (-> session-admin
                  (request auto-update-op-url)
                  ltu/body->edn
                  (ltu/is-status 200)))

            (-> session-admin
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value (comp count :deployments-to-add) :operational-status 0)
                (ltu/is-key-value :state utils/state-updated))

            (with-redefs [utils/query-nuvlaboxes-as (constantly [{:id ne-id-1}])]
              (-> session-admin
                  (request auto-update-op-url)
                  ltu/body->edn
                  (ltu/is-status 202)))

            (-> session-admin
                (request dep-set-url)
                ltu/body->edn
                (ltu/is-status 200)
                (ltu/is-key-value (comp count :deployments-to-add) :operational-status 1)
                (ltu/is-key-value :state utils/state-updating))
            ))))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id t/resource-type))]
    (ltu/verify-405-status [[base-uri crud/action-delete]
                            [resource-uri :post]])))
