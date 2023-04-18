(ns sixsq.nuvla.server.resources.deployment-set-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.deployment :as deployment]
    [sixsq.nuvla.server.resources.deployment-set :as t]
    [sixsq.nuvla.server.resources.deployment-set.utils :as dep-set-utils]
    [sixsq.nuvla.server.resources.module.utils :as module-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context t/resource-type))
(def deployment-base-uri (str p/service-context deployment/resource-type))

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

(def valid-deployment-set {:name  dep-set-name,
                           :start false,
                           :applications-sets
                           dep-apps-sets})

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
     "module-applications-sets/74e49539-92f8-4537-94a8-f74e8c3b773f",
     :author "jane@example.com",
     :commit "add app 3 and 4"}
    {:href
     "module-applications-sets/9a5aa5e5-4929-42b7-9064-c0fd0c6d5a75",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/7c676f67-22ec-441e-bb0a-02e4b00ea5b3",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/a906d3b4-bb2e-47a6-98f5-f838a1b85bfd",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/09642eb3-113f-45f2-8dbb-3e9205c44e48",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/9a71bc16-764d-452c-8600-213822a72156",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/c715fb27-c347-49fa-b27b-d1b9d1ff897a",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/b046abb2-882b-4f9c-bd50-84cdca29272f",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href
     "module-applications-sets/60874868-dee3-4dca-9a04-f63a223d6c76",
     :author "jane@example.com",
     :commit "set-1 app 4 moved to v2"}
    {:href
     "module-applications-sets/b736f651-17a3-40fe-8777-44dc29f9746e",
     :author "jane@example.com",
     :commit "set 1 app 1 to v0"}
    {:href
     "module-applications-sets/4fef2c2c-ddf4-4305-987d-22116a35f9c7",
     :author    "jane@example.com",
     :commit    "edit set-1 app 4 var1 overwrite",
     :published true}],
   :published                 true,
   :subtype                   "applications_sets"}
  )

(def u-application-app1-v0
  {:description               "# App 1",
   :path                      "khaledvendor1/app-1",
   :license                   {:url "https://khaled-vendor-1", :name "app1"},
   :compatibility             "swarm",
   :content
   {:updated              "2022-12-14T12:46:41.941Z",
    :created              "2022-12-14T12:46:41.941Z",
    :output-parameters    [],
    :author               "khaled+vendor-1@sixsq.com",
    :requires-user-rights false,
    :created-by           "internal",
    :id                   "module-application/f584be23-542a-48b3-8855-55e9b37d8460",
    :commit               "first commit",
    :docker-compose
    "version: \"4.7\"\nservices:\n  web:\n    image: nginx:latest\n"},
   :updated                   "2023-01-19T15:25:55.514Z",
   :name                      "App 1",
   :created                   "2022-12-14T12:46:42.877Z",
   :parent-path               "khaledvendor1",
   :data-accept-content-types [],
   :updated-by                "user/jane",
   :created-by                "user/0925fec3-a56a-4301-97f8-2fc9e177d153",
   :id                        "module/fcc71f74-1898-4e38-a284-5997141801a7",
   :resource-type             "module",
   :acl
   {:edit-data ["group/nuvla-admin"],
    :owners    ["user/0925fec3-a56a-4301-97f8-2fc9e177d153"],
    :view-acl  ["group/dev" "group/nuvla-admin" "group/nuvla-user"],
    :delete    ["group/nuvla-admin"],
    :view-meta ["group/dev" "group/nuvla-admin" "group/nuvla-user"],
    :edit-acl  ["group/nuvla-admin"],
    :view-data ["group/dev" "group/nuvla-admin" "group/nuvla-user"],
    :manage    ["group/nuvla-admin"],
    :edit-meta ["group/nuvla-admin"]},
   :logo-url                  "/ui/images/noimage.png",
   :versions
   [{:href      "module-application/f584be23-542a-48b3-8855-55e9b37d8460",
     :author    "khaled+vendor-1@sixsq.com",
     :commit    "first commit",
     :published true}
    {:href   "module-application/c96263a6-6224-482f-b2b3-b77595575995",
     :author "khaled+vendor-1@sixsq.com",
     :commit "no commit message"}],
   :price
   {:price-id          "price_1MGjPoHG9PNMTNBOcSeVxuMN",
    :product-id        "prod_Mys6H1CJp9JmJq",
    :account-id        "acct_1MEuEAH976Q5QPmL",
    :cent-amount-daily 300,
    :currency          "EUR"},
   :published                 true,
   :subtype                   "application"}
  )

(def u-application-app2-v0
  {:valid                     false,
   :description               "# App 2",
   :path                      "khaledvendor1/app-2",
   :compatibility             "swarm",
   :content
   {:updated              "2022-12-20T16:16:08.702Z",
    :created              "2022-12-20T16:16:08.702Z",
    :output-parameters    [],
    :author               "khaled+vendor-1@sixsq.com",
    :requires-user-rights false,
    :created-by           "internal",
    :id                   "module-application/12c7ff54-ee35-4894-859a-2bc306e5e45d",
    :commit               "no commit message",
    :docker-compose
    "version: \"4.7\"\nservices:\n  web:\n    image: nginx:latest\n"},
   :updated                   "2023-01-19T15:45:11.428Z",
   :name                      "App 2",
   :created                   "2022-12-20T16:16:08.720Z",
   :parent-path               "khaledvendor1",
   :data-accept-content-types [],
   :updated-by                "user/jane",
   :created-by                "user/0925fec3-a56a-4301-97f8-2fc9e177d153",
   :id                        "module/770f5090-be33-42a3-b9fe-0de4622f12ea",
   :validation-message
   "Version \"4.7\" in \"/var/folders/fd/grmkjs213fv25cgll9_2s63m0000gn/T/tmpmba49mjz/docker-compose.yaml\" is invalid.\n",
   :resource-type             "module",
   :acl
   {:edit-data ["group/nuvla-admin"],
    :owners    ["user/0925fec3-a56a-4301-97f8-2fc9e177d153"],
    :view-acl  ["group/dev" "group/nuvla-admin" "group/nuvla-user"],
    :delete    ["group/nuvla-admin"],
    :view-meta ["group/dev" "group/nuvla-admin" "group/nuvla-user"],
    :edit-acl  ["group/nuvla-admin"],
    :view-data ["group/dev" "group/nuvla-admin" "group/nuvla-user"],
    :manage    ["group/nuvla-admin"],
    :edit-meta ["group/nuvla-admin"]},
   :logo-url                  "/ui/images/noimage.png",
   :versions
   [{:href      "module-application/12c7ff54-ee35-4894-859a-2bc306e5e45d",
     :author    "khaled+vendor-1@sixsq.com",
     :commit    "no commit message",
     :published true}
    {:href   "module-application/cbea75dc-1789-48e9-be16-a49b6ba99335",
     :author "group/nuvla-admin",
     :commit "no commit message"}],
   :price
   {:price-id          "price_1MH8VKHG9PNMTNBO8q49h0uk",
    :product-id        "prod_N1Ar0QfrskvDbR",
    :account-id        "acct_1MEuEAH976Q5QPmL",
    :cent-amount-daily 10,
    :currency          "EUR"},
   :published                 true,
   :subtype                   "application"})

(def u-applicaiton-app3-v0
  {:description               "app 3",
   :path                      "khaled/app-3",
   :compatibility             "swarm",
   :content
   {:updated              "2023-01-06T10:07:02.996Z",
    :created              "2023-01-06T10:07:02.996Z",
    :output-parameters    [],
    :author               "jane@example.com",
    :requires-user-rights false,
    :created-by           "internal",
    :id                   "module-application/34df90fc-392a-4d8e-aad2-185710e447c5",
    :commit               "no commit message",
    :docker-compose       "x"},
   :updated                   "2023-01-06T10:07:03.031Z",
   :name                      "App 3",
   :created                   "2023-01-06T10:07:03.031Z",
   :parent-path               "khaled",
   :data-accept-content-types [],
   :created-by                "user/jane",
   :id                        "module/188555b1-2006-4766-b287-f60e5e908197",
   :resource-type             "module",
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
   [{:rel "edit", :href "module/188555b1-2006-4766-b287-f60e5e908197"}
    {:rel "delete", :href "module/188555b1-2006-4766-b287-f60e5e908197"}
    {:rel "validate-docker-compose",
     :href
     "module/188555b1-2006-4766-b287-f60e5e908197/validate-docker-compose"}
    {:rel  "publish",
     :href "module/188555b1-2006-4766-b287-f60e5e908197_0/publish"}
    {:rel  "unpublish",
     :href "module/188555b1-2006-4766-b287-f60e5e908197_0/unpublish"}
    {:rel "delete-version",
     :href
     "module/188555b1-2006-4766-b287-f60e5e908197_0/delete-version"}],
   :logo-url                  "/ui/images/noimage.png",
   :versions
   [{:href   "module-application/34df90fc-392a-4d8e-aad2-185710e447c5",
     :author "jane@example.com",
     :commit "no commit message"}],
   :subtype                   "application"}
  )

(def u-application-app4-v1
  {:valid                     false,
   :description               "app 4",
   :path                      "khaled/app-4",
   :compatibility             "swarm",
   :content
   {:environmental-variables
    [{:name        "var_1_value",
      :required    false,
      :value       "some value",
      :description "Var 1"}
     {:name "var_2", :required false, :description "Var 2"}],
    :updated              "2023-01-17T10:15:58.188Z",
    :created              "2023-01-17T10:15:58.188Z",
    :output-parameters    [],
    :author               "jane@example.com",
    :requires-user-rights false,
    :created-by           "internal",
    :id                   "module-application/b89b3e34-2665-4a02-b15f-86f0db2737a0",
    :commit               "add vars",
    :docker-compose       "x"},
   :updated                   "2023-01-17T10:44:27.682Z",
   :name                      "App 4",
   :created                   "2023-01-06T10:07:26.540Z",
   :parent-path               "khaled",
   :data-accept-content-types [],
   :updated-by                "group/nuvla-admin",
   :created-by                "user/jane",
   :id                        "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
   :validation-message        "string indices must be integers",
   :resource-type             "module",
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
   [{:rel "edit", :href "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"}
    {:rel "delete", :href "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"}
    {:rel "validate-docker-compose",
     :href
     "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8/validate-docker-compose"}
    {:rel  "publish",
     :href "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8_1/publish"}
    {:rel  "unpublish",
     :href "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8_1/unpublish"}
    {:rel "delete-version",
     :href
     "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8_1/delete-version"}],
   :logo-url                  "/ui/images/noimage.png",
   :versions
   [{:href   "module-application/cc378a37-ecdb-4297-9dfa-95dad96bafc2",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href   "module-application/b89b3e34-2665-4a02-b15f-86f0db2737a0",
     :author "jane@example.com",
     :commit "add vars"}
    {:href   "module-application/f6051d88-920e-4f30-8e36-bdfcbf7e1fad",
     :author "jane@example.com",
     :commit "no commit message"}],
   :subtype                   "application"}
  )

(def u-application-nginx-v5
  {:description               "Nginx PRO",
   :path                      "marketplace/nginx-pro",
   :license
   {:url         "https://sixsq.com/terms/general-terms-and-conditions",
    :name        "SixSq",
    :description "SixSq license"},
   :content
   {:architectures     ["amd64"],
    :mounts            [],
    :updated           "2020-10-05T09:36:26.124Z",
    :created           "2020-10-05T09:36:26.124Z",
    :private-registries
    ["infrastructure-service/c40f703b-f6c0-41d3-bdfd-b51d50b03372"],
    :output-parameters [],
    :author            "jane@example.com",
    :ports             [],
    :registries-credentials
    ["credential/8188fda6-a4af-4dcf-ba54-42544734f8fa"],
    :created-by        "internal",
    :id                "module-component/d8defff5-689f-4be0-9179-bf35d6e625a1",
    :commit            "no commit message",
    :image             {:image-name "test-private-repo", :repository "0xbase12"}},
   :updated                   "2022-01-17T16:07:42.921Z",
   :name                      "Nginx PRO",
   :created                   "2020-09-29T14:11:31.147Z",
   :parent-path               "marketplace",
   :data-accept-content-types [],
   :updated-by                "user/jane",
   :created-by                "user/jane",
   :id                        "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2",
   :resource-type             "module",
   :acl
   {:edit-data ["group/nuvla-admin"],
    :owners    ["user/jane"],
    :view-acl  ["group/nuvla-admin" "group/nuvla-user"],
    :delete    ["group/nuvla-admin"],
    :view-meta ["group/nuvla-admin" "group/nuvla-user"],
    :edit-acl  ["group/nuvla-admin"],
    :view-data ["group/nuvla-admin" "group/nuvla-user"],
    :manage    ["group/nuvla-admin"],
    :edit-meta ["group/nuvla-admin"]},
   :operations
   [{:rel "edit", :href "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2"}
    {:rel "delete", :href "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2"}
    {:rel  "publish",
     :href "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2_5/publish"}
    {:rel  "unpublish",
     :href "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2_5/unpublish"}
    {:rel "delete-version",
     :href
     "module/64e8d02d-1b40-46d0-b1d8-2093024fc1d2_5/delete-version"}],
   :logo-url                  "/ui/images/noimage.png",
   :versions
   [{:href   "module-component/b388f6db-def0-421a-9c55-d144f6c0f68f",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href   "module-component/41af33ac-f576-470c-8a58-969bdb0f5dd5",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href   "module-component/19a0ea00-2a9b-408f-806d-160f1283b555",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href   "module-component/92a0f1b8-8a2e-4888-9dbe-db2a00183da9",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href   "module-component/324bb324-fcdf-42cb-a076-a235e7057bbb",
     :author "jane@example.com",
     :commit "no commit message"}
    {:href      "module-component/d8defff5-689f-4be0-9179-bf35d6e625a1",
     :author    "jane@example.com",
     :commit    "no commit message",
     :published true}],
   :price
   {:price-id              "price_1Ht96vHG9PNMTNBOMgErjhmA",
    :product-id            "prod_I6xTMp0LLWdmGi",
    :account-id            "acct_1HWjCnLttEVQ9FeH",
    :cent-amount-daily     300,
    :currency              "EUR",
    :follow-customer-trial true},
   :published                 true,
   :subtype                   "component"}
  )

(def u-application-kube-v0
  {:description               "# Kube app",
   :path                      "khaled/kube-app",
   :license
   {:url         "https://opensource.org/licenses/Apache-2.0",
    :name        "Apache 2.0",
    :description "Apache License, Version 2.0, January 2004"},
   :content
   {:updated              "2022-09-14T14:31:38.182Z",
    :created              "2022-09-14T14:31:38.182Z",
    :output-parameters    [],
    :author               "jane@example.com",
    :requires-user-rights false,
    :created-by           "internal",
    :id                   "module-application/cadb813c-a701-4d75-85f0-caedb57a2ab2",
    :commit               "no commit message",
    :docker-compose       "x"},
   :updated                   "2022-09-14T14:31:38.205Z",
   :name                      "Kube app",
   :created                   "2022-09-14T14:31:38.205Z",
   :parent-path               "khaled",
   :data-accept-content-types [],
   :created-by                "user/jane",
   :id                        "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e",
   :resource-type             "module",
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
   [{:rel "edit", :href "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e"}
    {:rel "delete", :href "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e"}
    {:rel  "publish",
     :href "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e_0/publish"}
    {:rel  "unpublish",
     :href "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e_0/unpublish"}
    {:rel "delete-version",
     :href
     "module/1cefb94b-c527-4b8a-be5f-802b131c1a9e_0/delete-version"}],
   :logo-url                  "/ui/images/noimage.png",
   :versions
   [{:href   "module-application/cadb813c-a701-4d75-85f0-caedb57a2ab2",
     :author "jane@example.com",
     :commit "no commit message"}],
   :subtype                   "application_kubernetes"}
  )


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
            :credential  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:environmental-variables [{:name  "var_1_value"
                                                     :value "overwritten var1 overwritten in deployment set"}
                                                    {:name  "var_2"
                                                     :value "overwritten in deployment set"}]
                          :id                      "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8"
                          :version                 1}
            :credential  "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      "module/188555b1-2006-4766-b287-f60e5e908197"
                          :version 0}
            :credential  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      "module/188555b1-2006-4766-b287-f60e5e908197"
                          :version 0}
            :credential  "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      "module/770f5090-be33-42a3-b9fe-0de4622f12ea"
                          :version 0}
            :credential  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      "module/770f5090-be33-42a3-b9fe-0de4622f12ea"
                          :version 0}
            :credential  "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}
           {:app-set     "set-1"
            :application {:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                          :version 0}
            :credential  "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316"}
           {:app-set     "set-1"
            :application {:id      "module/fcc71f74-1898-4e38-a284-5997141801a7"
                          :version 0}
            :credential  "credential/bc258c46-4771-45d3-9b38-97afdf185f44"}})))

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
      (let [{{{:keys [resource-id
                      location]} :body}
             :response} (-> session-user
                            (request base-uri
                                     :request-method :post
                                     :body (json/write-str valid-deployment-set))
                            (ltu/body->edn)
                            (ltu/is-status 202))

            dep-set-url       (str p/service-context resource-id)
            authn-payload     {"authn-info" {"active-claim" "user/jane"
                                             "claims"       ["group/nuvla-anon"
                                                             "user/jane"
                                                             "group/nuvla-user"
                                                             session-id]
                                             "user-id"      "user/jane"}}
            job-payload-start (merge authn-payload
                                     {"filter" (str "deployment-set='" resource-id "' "
                                                    "and (state='CREATED' or state='STOPPED')"
                                                    )})
            job-payload-stop  (merge authn-payload
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
              (ltu/is-key-value :state "CREATING")
              (ltu/is-operation-present :plan)
              (ltu/is-operation-present :create)
              (ltu/is-key-value :applications-sets dep-apps-sets)))

        (testing "user plan action should be built on demand"
          (with-redefs [dep-set-utils/resolve-application
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
                (= [{:credential "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                     :application
                     {:id "module/188555b1-2006-4766-b287-f60e5e908197", :version 0},
                     :app-set "set-1"}
                    {:credential "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                     :application
                     {:id "module/188555b1-2006-4766-b287-f60e5e908197", :version 0},
                     :app-set "set-1"}
                    {:credential "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                     :application
                     {:id "module/770f5090-be33-42a3-b9fe-0de4622f12ea", :version 0},
                     :app-set "set-1"}
                    {:credential "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                     :application
                     {:id "module/770f5090-be33-42a3-b9fe-0de4622f12ea", :version 0},
                     :app-set "set-1"}
                    {:credential "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                     :application
                     {:id "module/fcc71f74-1898-4e38-a284-5997141801a7", :version 0},
                     :app-set "set-1"}
                    {:credential "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                     :application
                     {:id "module/fcc71f74-1898-4e38-a284-5997141801a7", :version 0},
                     :app-set "set-1"}
                    {:credential "credential/bc258c46-4771-45d3-9b38-97afdf185f44",
                     :application
                     {:id "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
                      :version 1,
                      :environmental-variables
                      [{:name "var_1_value",
                        :value "overwritten var1 overwritten in deployment set"}
                       {:name "var_2", :value "overwritten in deployment set"}]},
                     :app-set "set-1"}
                    {:credential "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316",
                     :application
                     {:id "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8",
                      :version 1,
                      :environmental-variables
                      [{:name "var_1_value",
                        :value "overwritten var1 overwritten in deployment set"}
                       {:name "var_2", :value "overwritten in deployment set"}]},
                     :app-set "set-1"}])
                (is))))

        (testing "job is created"
          (-> session-user
              (request (str p/service-context location))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :action "create_deployment_set")
              (ltu/is-key-value :href :target-resource resource-id)))

        (testing "start action will create a start_deployment_set job"
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (-> session-user
                                 (request dep-set-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body)
                                 (assoc :state "CREATED")
                                 (json/write-str)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "CREATED"))
          (let [job-url (-> session-user
                            (request (str dep-set-url "/start"))
                            (ltu/body->edn)
                            (ltu/is-status 202)
                            (ltu/location-url))]
            (-> session-user
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "start_deployment_set")
                (ltu/is-key-value json/read-str :payload job-payload-start))))

        (testing "stop will not be possible in state CREATED"
          (-> session-user
              (request (str dep-set-url "/stop"))
              (ltu/body->edn)
              (ltu/is-status 409)))

        (testing "stop action will create a stop_deployment_set job"
          (-> session-user
              (request dep-set-url
                       :request-method :put
                       :body (-> session-user
                                 (request dep-set-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body)
                                 (assoc :state "STARTED")
                                 (json/write-str)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "STARTED"))
          (let [job-url (-> session-user
                            (request (str dep-set-url "/stop"))
                            (ltu/body->edn)
                            (ltu/is-status 202)
                            (ltu/location-url))]
            (-> session-user
                (request job-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :href :target-resource resource-id)
                (ltu/is-key-value :action "stop_deployment_set")
                (ltu/is-key-value json/read-str :payload job-payload-stop))))
        ))
    ))

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
                          (ltu/is-status 202))

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
