(ns sixsq.nuvla.server.resources.api-docs-openapi-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [sixsq.nuvla.server.resources.api-docs.openapi :refer [openapi-spec]]
    [sixsq.nuvla.server.resources.resource-metadata :as t]
    [sixsq.nuvla.server.resources.spec.resource-metadata-action-test :as action]
    [sixsq.nuvla.server.resources.spec.resource-metadata-attribute-test :as attribute]
    [sixsq.nuvla.server.resources.spec.resource-metadata-capability-test :as capability]))


(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-acl {:owners   ["group/nuvla-admin"]
                :edit-acl ["group/nuvla-admin"]})


(def resource-type-name "abcdef")


(def resource-metadata
  {:id            (str t/resource-type "/" resource-type-name)
   :resource-type t/resource-type
   :name          resource-type-name
   :type-uri      resource-type-name
   :created       timestamp
   :updated       timestamp
   :acl           valid-acl
   :actions       [action/valid]
   :attributes    attribute/valid-attributes
   :capabilities  [capability/valid]})


(deftest openapi-spec-generation
  (let [general {:openapi      "3.0.3"
                 :info         {:title          "Nuvla API"
                                :description    "HTTP-based RESTful API, for the management of Nuvla resources."
                                :termsOfService "https://docs.nuvla.io/legal/legal/"
                                :contact        {:name "API Support", :email "apiteam@nuvla.io"}
                                :license        {:name "Apache 2.0", :url "https://www.apache.org/licenses/LICENSE-2.0.html"}
                                :version        "<version>"}
                 :externalDocs {:description "Find out more about Nuvla", :url "https://docs.nuvla.io/nuvla/user-guide"}
                 :servers      [{:url "https://nuvla.io/api", :description "Main (production) server"}]}]
    (testing "Simple resource without scrud operations"
      (is (= (assoc general
               :tags [{:name "abcdef"}]
               :paths {}
               :components {:schemas {"Abcdef" {}}})
             (openapi-spec [resource-metadata]))))
    (testing "Resource with resource get operation"
      (is (= (assoc general
               :tags [{:name "abcdef"}]
               :paths {"/abcdef/{resource-id}"
                       {:parameters [{:name        "resource-id"
                                      :in          "path"
                                      :required    true
                                      :description "The ID of the abcdef"
                                      :schema      {:type "string", :format "uuid"}}]
                        :get        {:tags        ["abcdef"]
                                     :description "Returns a abcdef"
                                     :responses   {200 {:description "A abcdef"
                                                        :content     {"application/json"
                                                                      {:schema {:$ref "#/components/schemas/Abcdef"}}}}
                                                   403 {:description "Not authorized"}
                                                   404 {:description "The resource was not found"}}}}}
               :components {:schemas {"Abcdef" {}}})
             (openapi-spec [(assoc resource-metadata
                              :scrud-operations [:get])]))))))

