(ns com.sixsq.nuvla.server.resources.credential-api-key-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-api-key :as t]
    [com.sixsq.nuvla.server.resources.credential-template :as ct]
    [com.sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [com.sixsq.nuvla.server.resources.credential.key-utils :as key-utils]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [environ.core :as env]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" akey/resource-url)))


(deftest check-strip-session-role
  (is (= ["alpha" "beta"] (t/strip-session-role ["alpha" "session/2d273461-2778-4a66-9017-668f6fed43ae" "beta"])))
  (is (= [] (t/strip-session-role ["session/2d273461-2778-4a66-9017-668f6fed43ae"]))))


(deftest lifecycle
  (let [session                     (-> (ltu/ring-app)
                                        session
                                        (content-type "application/json"))
        session-admin               (header session authn-info-header
                                            "group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user                (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon                (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

        name-attr                   "name"
        description-attr            "description"
        tags-attr                   ["one", "two"]

        href                        (str ct/resource-type "/" akey/method)
        template-url                (str p/service-context ct/resource-type "/" akey/method)

        template                    (-> session-admin
                                        (request template-url)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/body))

        create-import-no-href       {:template (ltu/strip-unwanted-attrs template)}

        create-import-href          {:name        name-attr
                                     :description description-attr
                                     :tags        tags-attr
                                     :template    {:href href
                                                   :ttl  1000}}

        create-import-href-zero-ttl {:template {:href href
                                                :ttl  0}}

        create-import-href-no-ttl   {:template {:href href}}]

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (if (env/env :nuvla-super-password)
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit))
      (-> session-admin
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit)))

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
                   :body (j/write-value-as-string create-import-no-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; creating a new credential as anon will fail; expect 400 because href cannot be accessed
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (j/write-value-as-string create-import-href))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; create a credential as a normal user
    (let [resp       (-> session-user
                         (request base-uri
                                  :request-method :post
                                  :body (j/write-value-as-string create-import-href))
                         (ltu/body->edn)
                         (ltu/is-status 201))
          id         (ltu/body-resource-id resp)
          secret-key (get-in resp [:response :body :secret-key])
          uri        (-> resp
                         (ltu/location))
          abs-uri    (str p/service-context uri)]

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
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit)))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags
                    digest expiry claims]} (-> session-user
                                               (request abs-uri)
                                               (ltu/body->edn)
                                               (ltu/is-status 200)
                                               (ltu/body))]
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
    (let [resp       (-> session-user
                         (request base-uri
                                  :request-method :post
                                  :body (j/write-value-as-string create-import-href-no-ttl))
                         (ltu/body->edn)
                         (ltu/is-status 201))
          id         (ltu/body-resource-id resp)
          secret-key (get-in resp [:response :body :secret-key])
          uri        (-> resp
                         (ltu/location))
          abs-uri    (str p/service-context uri)]

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
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit)))

      ;; ensure credential contains correct information
      (let [{:keys [digest expiry claims]} (-> session-user
                                               (request abs-uri)
                                               (ltu/body->edn)
                                               (ltu/is-status 200)
                                               (ltu/body))]
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
    (let [resp       (-> session-user
                         (request base-uri
                                  :request-method :post
                                  :body (j/write-value-as-string create-import-href-zero-ttl))
                         (ltu/body->edn)
                         (ltu/is-status 201))
          id         (ltu/body-resource-id resp)
          secret-key (get-in resp [:response :body :secret-key])
          uri        (-> resp
                         (ltu/location))
          abs-uri    (str p/service-context uri)]

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
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit)))

      ;; ensure credential contains correct information
      (let [{:keys [digest expiry claims] :as current} (-> session-user
                                                           (request abs-uri)
                                                           (ltu/body->edn)
                                                           (ltu/is-status 200)
                                                           (ltu/body))]
        (is digest)
        (is (key-utils/valid? secret-key digest))
        (is (nil? expiry))
        (is claims)

        ;; update the credential by changing the name attribute for user should succeed
        ;; claims are not editable for user
        (-> session-user
            (request abs-uri
                     :request-method :put
                     :body (j/write-value-as-string
                             (assoc current
                               :name "UPDATED!"
                               :claims {:identity "super",
                                        :roles    ["group/nuvla-user" "group/nuvla-anon" "group/nuvla-admin"]})))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the attribute has been changed
        (let [expected (assoc current :name "UPDATED!")
              reread   (-> session-user
                           (request abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/body))]

          (is (= (dissoc expected :updated) (dissoc reread :updated :updated-by)))
          (is (not= (:updated expected) (:updated reread))))

        ;; update the credential by changing the name attribute
        ;; claims are editable for super
        (-> session-admin
            (request abs-uri
                     :request-method :put
                     :body (j/write-value-as-string
                             (assoc current
                               :name "UPDATED by super!"
                               :claims {:identity "super",
                                        :roles    ["group/nuvla-user" "group/nuvla-anon" "group/nuvla-admin"]})))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the attribute has been changed
        (let [expected (assoc current :name "UPDATED by super!"
                                      :claims {:identity "super",
                                               :roles    ["group/nuvla-user" "group/nuvla-anon" "group/nuvla-admin"]})
              reread   (-> session-admin
                           (request abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/body))]

          (is (= (dissoc expected :updated) (dissoc reread :updated :updated-by)))
          (is (not= (:updated expected) (:updated reread)))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


