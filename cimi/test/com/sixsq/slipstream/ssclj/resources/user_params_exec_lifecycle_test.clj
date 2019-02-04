(ns com.sixsq.slipstream.ssclj.resources.user-params-exec-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]

    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]

    [com.sixsq.slipstream.ssclj.resources.user-params :as up]
    [com.sixsq.slipstream.ssclj.resources.user-params-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.user-params-template-exec :as exec]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context (u/de-camelcase up/resource-name)))


(deftest lifecycle
  (let [href (str ct/resource-url "/" exec/params-type)
        template-url (str p/service-context ct/resource-url "/" exec/params-type)

        session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN")
        session-user (header session authn-info-header "jane USER ANON")
        session-user2 (header session authn-info-header "john USER ANON")
        session-anon (header session authn-info-header "unknown ANON")

        template (-> session-admin
                     (request template-url)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (get-in [:response :body]))
        create-from-templ {:userParamTemplate
                           (-> template
                               ltu/strip-unwanted-attrs
                               (merge {:defaultCloudService "foo-bar-baz"
                                       :timeout             30
                                       :sshPublicKey        "ssh-rsa ABCDE foo"}))}]

    ;; Query.
    ;; anonymous user collection query should fail
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; admin user collection query should succeed but be empty (no user
    ;; params created yet)
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))

    ;; Create.
    ;; only one document per parameter type is allowed per user
    (let [uri (-> session-user
                  (request base-uri
                           :request-method :post
                           :body (json/write-str create-from-templ))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))
          u1-abs-uri (str p/service-context (u/de-camelcase uri))]
      (-> session-user
          (request u1-abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; second create for the same user should fail with 409 Conflict
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-from-templ))
          (ltu/body->edn)
          (ltu/is-status 409))

      ;; another user can create ...
      (-> session-user2
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-from-templ))
          (ltu/body->edn)
          (ltu/is-status 201))

      ;; but only once
      (-> session-user2
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-from-templ))
          (ltu/body->edn)
          (ltu/is-status 409))

      ;; Edit.
      ;; user can edit the document
      (let [resource (-> session-admin
                         (request u1-abs-uri)
                         (ltu/body->edn)
                         :response
                         :body)
            time-out (+ (:timeout resource) 10)
            timeout-json (json/write-str (assoc resource :timeout time-out))
            verbosity (+ (:verbosityLevel resource) 1)
            verbosity-json (json/write-str (assoc resource :verbosityLevel verbosity))
            ssh-pub-keys (str (:sshPublicKey resource) "\nssh-rsa XYZ baz")
            ssh-keys-json (json/write-str (assoc resource :sshPublicKey ssh-pub-keys))]

        ;; anon user can NOT edit
        (-> session-anon
            (request u1-abs-uri
                     :request-method :put
                     :body timeout-json)
            (ltu/is-status 403))

        ;; another user can NOT edit
        (-> session-user2
            (request u1-abs-uri
                     :request-method :put
                     :body timeout-json)
            (ltu/is-status 403))

        ;; owner can edit
        (-> session-user
            (request u1-abs-uri
                     :request-method :put
                     :body timeout-json)
            (ltu/body->edn)
            (ltu/is-status 200))
        (is (= time-out (-> session-admin
                            (request u1-abs-uri)
                            (ltu/body->edn)
                            :response
                            :body
                            :timeout)))

        (-> session-user
            (request u1-abs-uri
                     :request-method :put
                     :body ssh-keys-json)
            (ltu/body->edn)
            (ltu/is-status 200))
        (is (= 2 (-> session-admin
                     (request u1-abs-uri)
                     (ltu/body->edn)
                     :response
                     :body
                     :sshPublicKey
                     (clojure.string/split #"\n")
                     count)))

        ;; super can edit
        (-> session-admin
            (request u1-abs-uri
                     :request-method :put
                     :body verbosity-json)
            (ltu/body->edn)
            (ltu/is-status 200))
        (is (= verbosity (-> session-admin
                             (request u1-abs-uri)
                             (ltu/body->edn)
                             :response
                             :body
                             :verbosityLevel)))

        ;; edit non-existent document - 404
        (-> session-user
            (request (str u1-abs-uri "-fake")
                     :request-method :put
                     :body verbosity-json)
            (ltu/body->edn)
            (ltu/is-status 404)))

      ;; Delete.
      ;; users can delete their user param documents
      (-> session-user
          (request u1-abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))
      (ltu/refresh-es-indices)
      (-> session-user
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 0))

      (let [resp (-> session-user2
                     (request base-uri)
                     (ltu/body->edn)
                     (ltu/is-status 200)
                     (ltu/is-count 1))
            uri (->> resp
                     :response
                     :body
                     :userParam
                     first
                     :id)
            abs-uri (str p/service-context (u/de-camelcase uri))]
        (-> session-user2
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))
        (ltu/refresh-es-indices)
        (-> session-user2
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 0))))))
