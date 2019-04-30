(ns sixsq.nuvla.server.resources.user-username-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-username-password :as username-password]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" username-password/resource-url)))


(deftest lifecycle
  (let [template-href (str user-tpl/resource-type "/" username-password/registration-method)

        template-url (str p/service-context template-href)

        session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon (header session authn-info-header "user/unknown group/nuvla-anon")]

    (let [template (-> session-admin
                       (request template-url)
                       (ltu/body->edn)
                       (ltu/is-status 200)
                       (get-in [:response :body]))

          name-attr "name"
          description-attr "description"
          tags-attr ["one", "two"]
          plaintext-password "Plaintext-password-1"

          uname-alt "user/jane"

          no-href-create {:template (ltu/strip-unwanted-attrs (assoc template
                                                                :password plaintext-password
                                                                :username "alice"))}
          href-create {:description description-attr
                       :tags        tags-attr
                       :template    {:href     template-href
                                     :password plaintext-password
                                     :username "user/jane"}}

          invalid-create (assoc-in href-create [:template :href] "user-template/unknown-template")

          bad-params-create (assoc-in href-create [:template :invalid] "BAD")]


      ;; user collection query should succeed but be empty for all users
      (doseq [session [session-anon session-user session-admin]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?)
            (ltu/is-operation-present "add")
            (ltu/is-operation-absent "delete")
            (ltu/is-operation-absent "edit")))

      ;; create a new user; fails without reference
      (doseq [session [session-anon session-user session-admin]]
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str no-href-create))
            (ltu/body->edn)
            (ltu/is-status 400)))

      ;; create with invalid template fails
      (doseq [session [session-anon session-user session-admin]]
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str invalid-create))
            (ltu/body->edn)
            (ltu/is-status 404)))

      ;; create with bad parameters fails
      (doseq [session [session-anon session-user session-admin]]
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str bad-params-create))
            (ltu/body->edn)
            (ltu/is-status 400)))

      ;; create user, username-password template is only accessible by admin
      (let [resp (-> session-admin
                     (request base-uri
                              :request-method :post
                              :body (json/write-str href-create))
                     (ltu/body->edn)
                     (ltu/is-status 201))
            user-id (get-in resp [:response :body :resource-id])

            username-id (get-in href-create [:template :username])

            session-created-user (header session authn-info-header (str user-id " group/nuvla-user group/nuvla-anon"))

            {:keys [credential-password] :as user} (-> session-created-user
                                                       (request (str p/service-context user-id))
                                                       (ltu/body->edn)
                                                       (ltu/is-status 200)
                                                       (get-in [:response :body]))]

        ;; verify the ACL of the user
        (let [user-acl (:acl user)]
          (is (some #{"group/nuvla-admin"} (:owners user-acl)))
          (is (some #{"group/nuvla-user"} (:view-meta user-acl)))

          ;; user should have all rights
          (doseq [right [:view-meta :view-data :view-acl
                         :edit-meta :edit-data :edit-acl
                         :manage :delete]]
            (is (some #{user-id} (right user-acl)))))

        ;; verify name attribute (should default to username)
        (is (= "user/jane" (:name user)))

        ; credential password is created and visible by the created user
        (-> session-created-user
            (request (str p/service-context credential-password))
            (ltu/body->edn)
            (ltu/is-status 200))

        (-> session-user
            (request (str p/service-context credential-password))
            (ltu/body->edn)
            (ltu/is-status 403))

        ; 1 identifier for the username is visible for the created user; find by identifier
        (-> session-created-user
            (content-type "application/x-www-form-urlencoded")
            (request (str p/service-context user-identifier/resource-type)
                     :request-method :put
                     :body (rc/form-encode {:filter (format "identifier='%s'" username-id)}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))

        ;; find identifiers by parent
        (-> session-created-user
            (content-type "application/x-www-form-urlencoded")
            (request (str p/service-context user-identifier/resource-type)
                     :request-method :put
                     :body (rc/form-encode {:filter (format "parent='%s'" user-id)}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))

        (-> session-admin
            (request (str p/service-context user-id))
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [{:keys [state] :as user} (-> session-created-user
                                           (request (str p/service-context user-id))
                                           (ltu/body->edn)
                                           :response
                                           :body)]
          (is (= "ACTIVE" state)))

        ;; try to create a second user with the same identifier
        ;; this must fail and all created supporting resources must be cleaned up
        (let [resp (-> session-admin
                       (request base-uri
                                :request-method :post
                                :body (json/write-str (assoc-in href-create [:template :username] "user/jane")))
                       (ltu/body->edn)
                       (ltu/is-status 409))
              user-id (get-in resp [:response :body :resource-id])]

          ; no dangling credentials
          (-> session-admin
              (content-type "application/x-www-form-urlencoded")
              (request (str p/service-context credential/resource-type)
                       :request-method :put
                       :body (rc/form-encode {:filter (format "parent='%s'" user-id)}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 0))

          ; no dangling identifiers
          (-> session-admin
              (content-type "application/x-www-form-urlencoded")
              (request (str p/service-context user-identifier/resource-type)
                       :request-method :put
                       :body (rc/form-encode {:filter (format "parent='%s'" user-id)}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-count 0)))


        ;; user can delete his account
        (-> session-created-user
            (request (str p/service-context user-id)
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; password credential is gone
        (-> session-created-user
            (request (str p/service-context credential-password))
            (ltu/body->edn)
            (ltu/is-status 404))

        ;; all identifiers pointing to user are gone
        (-> session-created-user
            (content-type "application/x-www-form-urlencoded")
            (request (str p/service-context user-identifier/resource-type)
                     :request-method :put
                     :body (rc/form-encode {:filter (format "parent='%s'" user-id)}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 0))

        ;; the user resource is gone as well
        (-> session-created-user
            (request (str p/service-context user-id))
            (ltu/body->edn)
            (ltu/is-status 404)))
      )))
