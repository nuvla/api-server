(ns sixsq.nuvla.server.resources.user-template-direct-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as ct]
    [sixsq.nuvla.server.resources.user-template-direct :as direct]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer :all]
    [ring.util.codec :as codec]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context user/resource-name))


(deftest check-metadata
  (mdtu/check-metadata-exists (str ct/resource-url "-" direct/resource-url)))


(deftest lifecycle
  (let [uname "120720737412@eduid.chhttps://eduid.ch/idp/shibboleth!https://fed-id.nuv.la/samlbridge/module.php/saml/sp/metadata.php/sixsq-saml-bridge!iqqrh4oiyshzcw9o40cvo0+pgka="
        href (str ct/resource-url "/" direct/registration-method)
        template-url (str p/service-context ct/resource-url "/" direct/registration-method)

        session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN")
        session-user (header session authn-info-header (format "%s USER ANON" uname))
        session-anon (header session authn-info-header "unknown ANON")
        session-admin-form (-> session-admin
                               (content-type u/form-urlencoded))

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (get-in [:response :body]))

        no-href-create {:template (ltu/strip-unwanted-attrs (assoc template
                                                              :username uname
                                                              :emailAddress "user@example.org"))}
        href-create {:template {:href         href
                                :username     uname
                                :emailAddress "user@example.org"}}
        invalid-create (assoc-in href-create [:template :href] "user-template/unknown-template")]

    ;; anonymous user collection query should succeed but be empty
    ;; access needed to allow self-registration
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; admin user collection query should succeed but be empty (no users created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; create a new user as administrator; fail without reference
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str no-href-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; anonymous create must fail; expect 400 because href cannot be accessed
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str href-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; anonymous create without template reference fails
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str no-href-create))
        (ltu/body->edn)
        (ltu/is-status 400))

    ;; admin create with invalid template fails
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str invalid-create))
        (ltu/body->edn)
        (ltu/is-status 404))

    ;; create a user via admin
    (let [create-req {:template {:href         href
                                 :username     uname
                                 :emailAddress "jane@example.org"}}
          resp (-> session-admin
                   (request base-uri
                            :request-method :post
                            :body (json/write-str create-req))
                   (ltu/body->edn)
                   (ltu/is-status 201))
          id (get-in resp [:response :body :resource-id])
          uri (-> resp
                  (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; creating same user a second time should fail
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-req))
          (ltu/body->edn)
          (ltu/is-status 409))

      ;; admin should be able to see, edit, and delete user
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))

      (-> session-user
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit"))

      ;; admin can delete resource
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))

    ;; check that user can be created with form
    ;; referenced template is only available to admin
    (let [resp (-> session-admin-form
                   (request base-uri
                            :request-method :post
                            :body (codec/form-encode {:href         href
                                                      :username     uname
                                                      :emailAddress "jane@example.org"}))
                   (ltu/body->edn)
                   (ltu/is-status 201))

          uri (-> resp ltu/location)
          abs-uri (str p/service-context uri)]

      ;; check resource exists
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; admin can delete resource
      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))
