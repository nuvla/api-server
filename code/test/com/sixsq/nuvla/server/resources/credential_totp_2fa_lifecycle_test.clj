(ns com.sixsq.nuvla.server.resources.credential-totp-2fa-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-template :as ct]
    [com.sixsq.nuvla.server.resources.credential-template-totp-2fa :as ct-2fa-totp]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" ct-2fa-totp/resource-url)))


(deftest lifecycle

  (let [session          (-> (ltu/ring-app)
                             session
                             (content-type "application/json"))
        session-admin    (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user     (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon     (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

        name-attr        "name"
        description-attr "description"
        tags-attr        ["one", "two"]

        href             (str ct/resource-type "/" ct-2fa-totp/method)
        template-url     (str p/service-context ct/resource-type "/" ct-2fa-totp/method)

        template         (-> session-admin
                             (request template-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/body))

        create-no-href   {:template (-> template
                                        ltu/strip-unwanted-attrs
                                        (assoc :secret "some-secret"))}

        create-href      {:name        name-attr
                          :description description-attr
                          :tags        tags-attr
                          :template    {:href  href
                                        :secret "some-secret"}}]

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (doseq [session [session-admin session-user]]
      (-> session
          (request (str base-uri "?filter=subtype='"
                        ct-2fa-totp/credential-subtype "'"))
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
                   :body (json/write-str create-no-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; creating a new credential as anon will fail; expect 400 because href cannot be accessed
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str create-href))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; create a credential as a normal user should fail
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str create-href))
        (ltu/body->edn)
        (ltu/is-status 400))

    (let [resp    (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str create-href))
                      (ltu/body->edn)
                      (ltu/is-status 201))
          id      (ltu/body-resource-id resp)
          uri     (-> resp
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; admin should be able to see and delete credential
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present :delete)
          (ltu/is-operation-present :edit))

      ;; other users should not be able to see the credential
      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; ensure credential contains correct information
      (let [{:keys [name description tags secret]} (-> session-admin
                                                            (request abs-uri)
                                                            (ltu/body->edn)
                                                            (ltu/is-status 200)
                                                            (ltu/body))]
        (is (= name name-attr))
        (is (= description description-attr))
        (is (= tags tags-attr))
        (is (= "some-secret" secret)))

      ;; delete the credential
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


