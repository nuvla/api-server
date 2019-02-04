(ns com.sixsq.slipstream.ssclj.resources.credential-ssh-public-key-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.credential :as credential]
    [com.sixsq.slipstream.ssclj.resources.credential-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.credential-template-ssh-key-pair :as skp]
    [com.sixsq.slipstream.ssclj.resources.credential-template-ssh-public-key :as spk]
    [com.sixsq.slipstream.ssclj.resources.credential.ssh-utils :as ssh-utils]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase credential/resource-url)))


(deftest lifecycle-import
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-user (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")

        href (str ct/resource-url "/" spk/method)
        template-url (str p/service-context ct/resource-url "/" spk/method)

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (get-in [:response :body]))

        imported-ssh-key-info (ssh-utils/generate)

        create-import-no-href {:credentialTemplate (ltu/strip-unwanted-attrs
                                                     (assoc template
                                                       :publicKey (:publicKey imported-ssh-key-info)))}

        create-import-href {:credentialTemplate {:href      href
                                                 :publicKey (:publicKey imported-ssh-key-info)}}

        invalid-create-href (assoc-in create-import-href [:credentialTemplate :href] "user-template/unknown-template")]

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

    ;; creating a new credential with bad key must return 400
    (-> session-user
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc-in create-import-href [:credentialTemplate :publicKey] "bad-ssh-key")))
        (ltu/body->edn)
        (ltu/is-status 400)
        (ltu/message-matches #".*invalid public key.*"))

    ;; create a credential as a normal user
    (let [resp (-> session-user
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-import-href))
                   (ltu/body->edn)
                   (ltu/is-status 201))
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
      (let [resource (-> session-user
                         (request abs-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         :response
                         :body)]
        (is (= "rsa" (:algorithm resource)))
        (is (= (:fingerprint resource) (:fingerprint imported-ssh-key-info)))
        (is (= (:publicKey resource) (:publicKey imported-ssh-key-info))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))

(deftest lifecycle-generate
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN USER ANON")
        session-user (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")

        href (str ct/resource-url "/" skp/method)
        template-url (str p/service-context ct/resource-url "/" skp/method)

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (get-in [:response :body]))

        create-import-href {:credentialTemplate {:href href}}]

    ;; create a credential as a normal user
    (let [resp (-> session-user
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-import-href))
                   (ltu/body->edn)
                   (ltu/is-status 201))
          id (get-in resp [:response :body :resource-id])
          private-key (get-in resp [:response :body :privateKey])
          uri (-> resp
                  (ltu/location))
          abs-uri (str p/service-context (u/de-camelcase uri))]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      ;; the private key must be returned as part of the 201 response
      (is private-key)

      ;; admin/user should be able to see, and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present "delete")
            (ltu/is-operation-present "edit")))

      ;; ensure credential contains correct information
      (let [current (-> session-user
                        (request abs-uri)
                        (ltu/body->edn)
                        (ltu/is-status 200)
                        :response
                        :body)]
        (is (= "rsa" (:algorithm current)))
        (is (:fingerprint current))
        (is (:publicKey current))

        ;; update the credential by changing the name attribute
        (-> session-user
            (request abs-uri
                     :request-method :put
                     :body (json/write-str (assoc current :name "UPDATED!")))
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
          (is (not= (:updated expected) (:updated reread)))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
