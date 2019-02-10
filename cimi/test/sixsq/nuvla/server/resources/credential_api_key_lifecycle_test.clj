(ns sixsq.nuvla.server.resources.credential-api-key-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-api-key :as t]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context credential/resource-type))


(deftest check-strip-session-role
  (is (= ["alpha" "beta"] (t/strip-session-role ["alpha" "session/2d273461-2778-4a66-9017-668f6fed43ae" "beta"])))
  (is (= [] (t/strip-session-role ["session/2d273461-2778-4a66-9017-668f6fed43ae"]))))

(deftest lifecycle
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-user (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")

        name-attr "name"
        description-attr "description"
        tags-attr ["one", "two"]

        href (str ct/resource-type "/" akey/method)
        template-url (str p/service-context ct/resource-type "/" akey/method)

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (get-in [:response :body]))

        create-import-no-href {:template (ltu/strip-unwanted-attrs template)}

        create-import-href {:name        name-attr
                            :description description-attr
                            :tags        tags-attr
                            :template    {:href href
                                          :ttl  1000}}

        create-import-href-zero-ttl {:template {:href href
                                                :ttl  0}}

        create-import-href-no-ttl {:template {:href href}}]

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

    ;; creating a new credential as anon will fail; expect 400 because href cannot be accessed
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
          id (get-in resp [:response :body :resource-id])
          secret-key (get-in resp [:response :body :secretKey])
          uri (-> resp
                  (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; the secret key must be returned as part of the 201 response
      (is secret-key)

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-present "edit")))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags
                    digest expiry claims]} (-> session-user
                                               (request abs-uri)
                                               (ltu/body->edn)
                                               (ltu/is-status 200)
                                               :response
                                               :body)]
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is digest)
        (is (key-utils/valid? secret-key digest))
        (is expiry)
        (is claims))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))

    ;; execute the same tests but now create an API key without an expiry date
    (let [resp (-> session-user
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-import-href-no-ttl))
                   (ltu/body->edn)
                   (ltu/is-status 201))
          id (get-in resp [:response :body :resource-id])
          secret-key (get-in resp [:response :body :secretKey])
          uri (-> resp
                  (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; the secret key must be returned as part of the 201 response
      (is secret-key)

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-present "edit")))

      ;; ensure credential contains correct information
      (let [{:keys [digest expiry claims]} (-> session-user
                                               (request abs-uri)
                                               (ltu/body->edn)
                                               (ltu/is-status 200)
                                               :response
                                               :body)]
        (is digest)
        (is (key-utils/valid? secret-key digest))
        (is (nil? expiry))
        (is claims))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))

    ;; and again, with a zero TTL (should be same as if TTL was not given)
    (let [resp (-> session-user
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-import-href-zero-ttl))
                   (ltu/body->edn)
                   (ltu/is-status 201))
          id (get-in resp [:response :body :resource-id])
          secret-key (get-in resp [:response :body :secretKey])
          uri (-> resp
                  (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; the secret key must be returned as part of the 201 response
      (is secret-key)

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-present "edit")))

      ;; ensure credential contains correct information
      (let [{:keys [digest expiry claims] :as current} (-> session-user
                                                           (request abs-uri)
                                                           (ltu/body->edn)
                                                           (ltu/is-status 200)
                                                           :response
                                                           :body)]
        (is digest)
        (is (key-utils/valid? secret-key digest))
        (is (nil? expiry))
        (is claims)

        ;; update the credential by changing the name attribute for user should succeed
        ;; claims are not editable for user
        (-> session-user
            (request abs-uri
                     :request-method :put
                     :body (json/write-str (assoc current :name "UPDATED!"
                                                          :claims {:identity "super",
                                                                   :roles    ["USER" "ANON" "ADMIN"]})))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the attribute has been changed
        (let [expected (assoc current :name "UPDATED!")
              reread (-> session-user
                         (request abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         :response
                         :body)]

          (is (= (dissoc expected :updated) (dissoc reread :updated)))
          (is (not= (:updated expected) (:updated reread))))

        ;; update the credential by changing the name attribute
        ;; claims are editable for super
        (-> session-admin
            (request abs-uri
                     :request-method :put
                     :body (json/write-str (assoc current :name "UPDATED by super!"
                                                          :claims {:identity "super",
                                                                   :roles    ["USER" "ANON" "ADMIN"]})))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the attribute has been changed
        (let [expected (assoc current :name "UPDATED by super!"
                                      :claims {:identity "super",
                                               :roles    ["USER" "ANON" "ADMIN"]})
              reread (-> session-admin
                         (request abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         :response
                         :body)]

          (is (= (dissoc expected :updated) (dissoc reread :updated)))
          (is (not= (:updated expected) (:updated reread)))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


