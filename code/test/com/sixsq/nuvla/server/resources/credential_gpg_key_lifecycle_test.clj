(ns com.sixsq.nuvla.server.resources.credential-gpg-key-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-template :as ct]
    [com.sixsq.nuvla.server.resources.credential-template-gpg-key :as ct-gpg-key]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" ct-gpg-key/resource-url)))


(deftest lifecycle

  (let [session          (-> (ltu/ring-app)
                             session
                             (content-type "application/json"))
        session-admin    (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user     (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-other    (header session authn-info-header "user/tarzan user/tarzan group/nuvla-user group/nuvla-anon")
        session-anon     (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

        name-attr        "name"
        description-attr "description"
        tags-attr        ["one", "two"]

        href             (str ct/resource-type "/" ct-gpg-key/method)
        template-url     (str p/service-context ct/resource-type "/" ct-gpg-key/method)

        template         (-> session-admin
                             (request template-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/body))

        upload           {:template (-> template
                                      ltu/strip-unwanted-attrs
                                      (assoc :href  href
                                             :public-key "mypublickey"))}

        upload-pvt-key   {:template (-> template
                                      ltu/strip-unwanted-attrs
                                      (assoc :href  href
                                             :public-key  "mypublickey"
                                             :private-key "******"))}

        create-no-href   {:template (-> template
                                      ltu/strip-unwanted-attrs
                                      (assoc :public-key  "mypublickey"))}

        create           {:name        name-attr
                          :description description-attr
                          :tags        tags-attr
                          :template    {:href  href}}]

    ;; check we can perform search with ordering by name
    (-> session-admin
        (request (str base-uri "?orderby=name:asc")
                 :request-method :put)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; admin/user query should succeed but be empty (no credentials created yet)
    (doseq [session [session-admin session-user]]
      (-> session
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
                   :body (json/write-str create-no-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; creating a new credential as anon will fail; expect 400 because href cannot be accessed
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; as a normal user
    ;;;;;
    ;; upload an existing gpg key without the private part
    (let [resp    (-> session-user
                      (request base-uri
                               :request-method :post
                               :body (json/write-str upload))
                      (ltu/body->edn)
                      (ltu/is-status 201))
          id      (ltu/body-resource-id resp)
          keypair (get-in resp [:response :body])
          uri     (-> resp
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit)))

      ;; other users should not be able to see the credential
      (-> session-other
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; ensure credential contains correct information
      (let [{:keys [public-key private-key]} (-> session-user
                                                 (request abs-uri)
                                                 (ltu/body->edn)
                                                 (ltu/is-status 200)
                                                 (ltu/body))]

        (is (= "mypublickey" public-key (:public-key keypair)))
        ; it is a custom user GPG key, and no private key was provided...so none was stored
        (is (= nil private-key (:private-key keypair))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))

    ;;;;;;
    ;; upload an existing gpg key and save the private key
    (let [resp    (-> session-user
                    (request base-uri
                      :request-method :post
                      :body (json/write-str upload-pvt-key))
                    (ltu/body->edn)
                    (ltu/is-status 201))
          id      (ltu/body-resource-id resp)
          keypair (get-in resp [:response :body])
          uri     (-> resp
                    (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; acl tests already done in the previous test above - won't repeat

      ;; ensure credential contains correct information
      (let [{:keys [public-key private-key]} (-> session-user
                                                 (request abs-uri)
                                                 (ltu/body->edn)
                                                 (ltu/is-status 200)
                                                 (ltu/body))]

        (is (= "mypublickey" public-key (:public-key keypair)))
        ; even though no private key is generated, the original one is still returned back in the response
        ; but since the private key was provided, this time it is stored in the server
        (is (= "******" private-key (:private-key keypair))))

      ;; delete the credential
      (-> session-user
        (request abs-uri
          :request-method :delete)
        (ltu/body->edn)
        (ltu/is-status 200))) ))


