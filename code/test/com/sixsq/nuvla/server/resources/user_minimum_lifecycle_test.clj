(ns com.sixsq.nuvla.server.resources.user-minimum-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.user :as user]
    [com.sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [com.sixsq.nuvla.server.resources.user-template :as user-tpl]
    [com.sixsq.nuvla.server.resources.user-template-minimum :as minimum]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" minimum/resource-url)))


(deftest lifecycle
  (let [template-href     (str user-tpl/resource-type "/" minimum/registration-method)

        template-url      (str p/service-context template-href)

        session           (-> (ltu/ring-app)
                              session
                              (content-type "application/json"))
        session-admin     (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user      (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon      (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

        template          (-> session-admin
                              (request template-url)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/body))

        description-attr  "description"
        tags-attr         ["one", "two"]

        no-href-create    {:template (ltu/strip-unwanted-attrs template)}

        href-create       {:description description-attr
                           :tags        tags-attr
                           :template    {:href     template-href
                                         :username "user/jane"
                                         :email    "jane@example.com"
                                         :password "Some-password-1?"}}

        invalid-create    (assoc-in href-create [:template :href] "user-template/unknown-template")

        bad-params-create (assoc-in href-create [:template :invalid] "BAD")]

    ;; user collection query should succeed but be empty for all users
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)))

    (-> session-admin
        (content-type "application/x-www-form-urlencoded")
        (request base-uri
                 :request-method :put
                 :body (rc/form-encode {:filter "name!='super'"}))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present :add)
        (ltu/is-operation-absent :delete)
        (ltu/is-operation-absent :edit))

    ;; create a new user; fails without reference
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string no-href-create))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; create with invalid template fails
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string invalid-create))
          (ltu/body->edn)
          (ltu/is-status 404)))

    ;; create with bad parameters fails
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string bad-params-create))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; user collection query should succeed but be empty for all users
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (content-type "application/x-www-form-urlencoded")
          (request base-uri
                   :request-method :put
                   :body (rc/form-encode {:filter "name!='super'"}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)
          (ltu/is-operation-present :add)
          (ltu/is-operation-absent :delete)
          (ltu/is-operation-absent :edit)))

    ;; create a new user; fails without reference
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string no-href-create))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; create with invalid template fails
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string invalid-create))
          (ltu/body->edn)
          (ltu/is-status 404)))

    ;; create with bad parameters fails
    (doseq [session [session-anon session-user session-admin]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string bad-params-create))
          (ltu/body->edn)
          (ltu/is-status 400)))

    ;; create user, minimum template is only accessible by admin
    (let [user-id              (-> session-admin
                                   (request base-uri
                                            :request-method :post
                                            :body (j/write-value-as-string href-create))
                                   (ltu/body->edn)
                                   (ltu/is-status 201)
                                   (ltu/location))

          username-id          (get-in href-create [:template :username])

          email                (get-in href-create [:template :email])

          session-created-user (header session authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon"))

          {user-acl    :acl
           email-id    :email
           password-id :credential-password
           user-name   :name :as _user} (-> session-created-user
                                            (request (str p/service-context user-id))
                                            (ltu/body->edn)
                                            (ltu/is-status 200)
                                            (ltu/body))]

      ;; verify the ACL of the user
      (is (some #{"group/nuvla-admin"} (:owners user-acl)))
      ;; user should have all rights
      (doseq [right [:view-meta :view-data :view-acl
                     :edit-meta :edit-data :edit-acl
                     :manage :delete]]
        (is (some #{user-id} (right user-acl))))

      ;; verify name attribute (should default to username)
      (is (= "user/jane" user-name))

      ; 1 identifier for the username is visible for the created user; find by identifier
      (-> session-created-user
          (content-type "application/x-www-form-urlencoded")
          (request (str p/service-context user-identifier/resource-type)
                   :request-method :put
                   :body (rc/form-encode {:filter (format "identifier='%s'" username-id)}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1))


      ;; email resource has been created and is visible for the user
      (-> session-created-user
          (request (str p/service-context email-id))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :validated true))


      (-> session-created-user
          (request (str p/service-context password-id))
          (ltu/body->edn)
          (ltu/is-status 200))

      ; 1 identifier for the email is visible for the created user; find by identifier
      (-> session-created-user
          (content-type "application/x-www-form-urlencoded")
          (request (str p/service-context user-identifier/resource-type)
                   :request-method :put
                   :body (rc/form-encode {:filter (format "identifier='%s'" email)}))
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
          (ltu/is-count 2))

      (-> session-admin
          (request (str p/service-context user-id))
          (ltu/body->edn)
          (ltu/is-status 200))

      (let [{:keys [state] :as _user} (-> session-created-user
                                          (request (str p/service-context user-id))
                                          (ltu/body->edn)
                                          (ltu/body))]
        (is (= "ACTIVE" state)))

      ;; user can delete his account
      (-> session-created-user
          (request (str p/service-context user-id)
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; all identifiers pointing to user are gone
      (-> session-created-user
          (content-type "application/x-www-form-urlencoded")
          (request (str p/service-context user-identifier/resource-type)
                   :request-method :put
                   :body (rc/form-encode {:filter (format "parent='%s'" user-id)}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 0))

      ;; the email resource is gone also
      (-> session-created-user
          (content-type "application/x-www-form-urlencoded")
          (request (str p/service-context email-id))
          (ltu/body->edn)
          (ltu/is-status 404))

      ;; the user resource is gone as well
      (-> session-created-user
          (request (str p/service-context user-id))
          (ltu/body->edn)
          (ltu/is-status 404)))))
