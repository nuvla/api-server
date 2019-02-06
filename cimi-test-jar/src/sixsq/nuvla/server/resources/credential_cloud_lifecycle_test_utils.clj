(ns sixsq.nuvla.server.resources.credential-cloud-lifecycle-test-utils
  (:require
    [cemerick.url :as url]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [are deftest is use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.connector :as con]
    [sixsq.nuvla.server.resources.connector-template :as cont]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))

(def base-uri (str p/service-context (u/de-camelcase credential/resource-url)))


(defmulti get-connector-template
          (fn [cloud-service-type instanceName]
            cloud-service-type))

(defmethod get-connector-template :default
  [cloud-service-type instanceName]
  (let [template-url (str p/service-context cont/resource-url "/" cloud-service-type)
        resp (-> (session (ltu/ring-app))
                 (content-type "application/json")
                 (header authn-info-header "internal ADMIN")
                 (request template-url)
                 (ltu/body->edn)
                 (ltu/is-status 200))
        template (get-in resp [:response :body])]
    {:connectorTemplate (-> template
                            ltu/strip-unwanted-attrs
                            (assoc :instanceName instanceName))}))

(defn create-connector-instance
  [cloud-service-type instanceName]
  (let [connector-create-uri (str p/service-context con/resource-url)
        href (str cont/resource-url "/" cloud-service-type)
        href-create (get-connector-template cloud-service-type instanceName)]
    (-> (session (ltu/ring-app))
        (content-type "application/json")
        (header authn-info-header "internal ADMIN")
        (request connector-create-uri
                 :request-method :post
                 :body (json/write-str href-create))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))))

(defn connector-instance-name
  [credential-template-data]
  (-> credential-template-data
      (get-in [:connector :href])
      (str/split #"/")
      (second)))

(defn cred-find
  [session conn-inst-name]
  (-> session
      (content-type "application/x-www-form-urlencoded")
      (request base-uri
               :request-method :put
               :body (url/map->query {:$filter  (format "type^='cloud-cred' and connector/href='connector/%s'" conn-inst-name)
                                      :$orderby "created:desc"
                                      :$last    1}))
      (ltu/body->edn)
      (ltu/is-status 200)))

(defn cred-create
  [session templ]
  (-> session
      (request base-uri
               :request-method :post
               :body (json/write-str templ))
      (ltu/body->edn)
      (ltu/is-status 201)))

(defn cred-edit
  [session {:keys [id] :as new-cred}]
  (-> session
      (request (str p/service-context id)
               :request-method :put
               :body (json/write-str new-cred))
      (ltu/body->edn)
      (ltu/is-status 200)))

(defn cred-take-first
  [response]
  (-> response
      (get-in [:response :body :credentials])
      first))

(defn cloud-cred-lifecycle
  [{cloud-method-href :href :as credential-template-data} cloud-service-type]
  (create-connector-instance cloud-service-type (connector-instance-name credential-template-data))
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-user (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")

        name-attr "name"
        description-attr "description"
        properties-attr {:a "one", :b "two"}

        href cloud-method-href
        template-url (str p/service-context cloud-method-href)

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (get-in [:response :body]))
        create-import-no-href {:credentialTemplate (ltu/strip-unwanted-attrs template)}

        create-import-href {:name               name-attr
                            :description        description-attr
                            :properties         properties-attr
                            :credentialTemplate credential-template-data}]

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present "add")
          (ltu/is-operation-absent "delete")
          (ltu/is-operation-absent "edit")))

    ;; anonymous credential collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; creating a new credential without reference will fail for all types of users
    (doseq [session [session-admin session-user session-anon]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-import-no-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; creating a new credential as anon will fail
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str create-import-href))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; create a credential as a normal user
    (let [resp (-> session-user
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-import-href))
                   (ltu/body->edn)
                   (ltu/is-status 201))
          _ (ltu/refresh-es-indices)
          id (get-in resp [:response :body :resource-id])
          uri (-> resp
                  (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-present "edit")))

      ;; ensure credential contains correct information
      (let [{:keys [name description
                    properties key secret]} (-> session-user
                                                (request abs-uri)
                                                (ltu/body->edn)
                                                (ltu/is-status 200)
                                                :response
                                                :body)]
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= properties properties-attr))
        (is (= (get-in create-import-href [:credentialTemplate :key]) key))
        (is (= (get-in create-import-href [:credentialTemplate :secret]) secret))

        ;; delete the credential
        (-> session-user
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))

    ;; Check that manual update cycle (search / merge / create new / delete old) works.
    (let [secret {:secret (str "new" (:secret credential-template-data))}
          conn-inst-name (connector-instance-name credential-template-data)

          ;; CREATION
          ;; create initial cred
          id (-> (cred-create session-user create-import-href)
                 (ltu/location))
          ;; another one as different user
          id-admin (-> (cred-create session-admin create-import-href)
                       (ltu/location))

          _ (ltu/refresh-es-indices)

          ;; UPDATE OPERATION
          ;; search and get - only one is expected
          old-doc (-> (cred-find session-user conn-inst-name)
                      (ltu/is-count 1)
                      cred-take-first)
          id-old (:id old-doc)
          ;; merge
          new-doc (-> (ltu/strip-unwanted-attrs old-doc)
                      (merge secret)
                      (assoc :href cloud-method-href))]

      ;; create new
      (-> (cred-create session-user {:credentialTemplate new-doc})
          (ltu/location))

      (ltu/refresh-es-indices)

      ;; VERIFY: check that both the old and new credentials are visible
      (-> (cred-find session-user conn-inst-name)
          (ltu/is-count 2))

      ;; DELETE: remove the old, original credential
      (-> session-user
          (request (str p/service-context id-old)
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      (ltu/refresh-es-indices)

      ;; VALIDATION
      (let [new-doc (-> (cred-find session-user conn-inst-name)
                        (ltu/is-count 1)
                        cred-take-first)
            id-new (:id new-doc)]

        (is (= (:secret secret) (:secret new-doc)))

        ;; CLEAN UP: remove the generated credentials
        (-> session-user
            (request (str p/service-context id-new)
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-admin
            (request (str p/service-context id-admin)
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))

    ;; Check that direct editing works as expected.
    (let [new-secret (str "new-" (:secret credential-template-data))
          conn-inst-name (connector-instance-name credential-template-data)

          ;; CREATION: create initial credential and get identifier
          id (-> (cred-create session-user create-import-href)
                 (ltu/location))]

      (ltu/refresh-es-indices)

      ;; VERIFY CREATION: ensure that document is visible and has the correct ID
      (let [old-doc (-> (cred-find session-user conn-inst-name)
                        (ltu/is-count 1)
                        cred-take-first)
            check-id (:id old-doc)]

        (is (= check-id id))

        ;; EDIT: change the secret in the credential
        (cred-edit session-user (assoc old-doc :secret new-secret)))

      (ltu/refresh-es-indices)

      ;; VERIFY EDIT: one credential should be visible and secret should have been changed
      (let [new-doc (-> (cred-find session-user conn-inst-name)
                        (ltu/is-count 1)
                        cred-take-first)
            id-new (:id new-doc)]

        (is (= id id-new))
        (is (= new-secret (:secret new-doc))))

      ;; DELETE: remove the created credential
      (-> session-user
          (request (str p/service-context id)
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      (ltu/refresh-es-indices))))
