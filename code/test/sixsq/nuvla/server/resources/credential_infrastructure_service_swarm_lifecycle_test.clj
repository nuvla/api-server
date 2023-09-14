(ns sixsq.nuvla.server.resources.credential-infrastructure-service-swarm-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as cred-tpl]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context credential/resource-type))
(def job-base-uri (str p/service-context job/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str credential/resource-type "-" cred-tpl/credential-subtype)))


(deftest lifecycle
  (let [session               (-> (ltu/ring-app)
                                  session
                                  (content-type "application/json"))
        session-admin         (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user          (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")
        session-anon          (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

        name-attr             "name"
        description-attr      "description"
        tags-attr             ["one", "two"]

        ca-value              "my-ca-certificate"
        cert-value            "my-public-certificate"
        key-value             "my-private-key"

        parent-value          "infrastructure-service/alpha"

        href                  (str ct/resource-type "/" cred-tpl/method)
        template-url          (str p/service-context ct/resource-type "/" cred-tpl/method)

        template              (-> session-admin
                                  (request template-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/body))

        create-import-no-href {:template (ltu/strip-unwanted-attrs template)}

        create-import-href    {:name        name-attr
                               :description description-attr
                               :tags        tags-attr
                               :template    {:href   href
                                             :parent parent-value
                                             :ca     ca-value
                                             :cert   cert-value
                                             :key    key-value}}
        authn-info-admin      {:user-id      "group/nuvla-admin"
                               :active-claim "group/nuvla-admin"
                               :claims       ["group/nuvla-admin" "group/nuvla-anon" "group/nuvla-user"]}
        authn-info-jane       {:user-id      "user/jane"
                               :active-claim "user/jane"
                               :claims       ["group/nuvla-anon" "user/jane" "group/nuvla-user"]}
        authn-info-anon       {:user-id      "user/unknown"
                               :active-claim "user/unknown"
                               :claims       #{"user/unknown" "group/nuvla-anon"}}]

    (testing "Admin/user query should succeed but be empty (no credentials created yet)"
      (doseq [session [session-admin session-user]]
        (-> session
            (request base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count zero?)
            (ltu/is-operation-present :add)
            (ltu/is-operation-absent :delete)
            (ltu/is-operation-absent :edit))))

    (testing "Anonymous credential collection query should not succeed"
      (-> session-anon
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (testing "Creating a new credential without reference will fail for all types of users"
      (doseq [[session event-owners authn-info]
              [[session-admin ["group/nuvla-admin"] authn-info-admin]
               [session-user ["group/nuvla-admin"] authn-info-jane]
               [session-anon ["group/nuvla-admin"] authn-info-anon]]]
        (-> session
            (request base-uri
                     :request-method :post
                     :body (json/write-str create-import-no-href))
            (ltu/body->edn)
            (ltu/is-status 400))

        (ltu/is-last-event nil
                           {:name               "credential.add"
                            :description        "credential.add attempt failed."
                            :category           "add"
                            :success            false
                            :linked-identifiers []
                            :authn-info         authn-info
                            :acl                {:owners event-owners}})))

    (testing "Creating a new credential as anon will fail; expect 400 because href cannot be accessed"
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str create-import-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    (let [resp    (testing "Create a credential as a normal user"
                    (-> session-user
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str create-import-href))
                        (ltu/body->edn)
                        (ltu/is-status 201)))
          id      (ltu/body-resource-id resp)
          uri     (-> resp
                      (ltu/location))
          abs-uri (str p/service-context uri)]

      ;; resource id and the uri (location) should be the same
      (is (= id uri))

      (ltu/is-last-event uri
                         {:name               "credential.add"
                          :description        (str "user/jane added credential " name-attr ".")
                          :category           "add"
                          :success            true
                          :linked-identifiers []
                          :authn-info         authn-info-jane
                          :acl                {:owners ["group/nuvla-admin" "user/jane"]}})

      ;; admin/user should be able to see and delete credential
      (doseq [session [session-admin session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :delete)
            (ltu/is-operation-present :edit)
            (ltu/is-operation-present :check)))

      (let [credential (testing "Ensure credential contains correct information"
                         (-> session-user
                             (request abs-uri)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-key-value :name name-attr)
                             (ltu/is-key-value :description description-attr)
                             (ltu/is-key-value :tags tags-attr)
                             (ltu/is-key-value :ca ca-value)
                             (ltu/is-key-value :cert cert-value)
                             (ltu/is-key-value :key key-value)
                             (ltu/is-key-value :parent parent-value)))]

        (testing "Ensure that the check action works"
          (let [op-url    (ltu/get-op credential "check")
                check-url (str p/service-context op-url)]

            (-> session-user
                (request check-url
                         :request-method :post)
                (ltu/body->edn)
                (ltu/is-status 202)))))

      (testing "Ensure that create and check credential created 2 cred check jobs"
        (let [descr-changed  "descr changed"
              job-url-filter (str job-base-uri "?filter=action='credential_check'&target-resource/href='" id "'")]
          (-> session-user
              (request job-url-filter
                       :request-method :put)
              (ltu/body->edn)
              (ltu/is-count 2))))

      ;; delete the credential
      (-> session-user
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      (ltu/is-last-event uri
                         {:name               "credential.delete"
                          :description        (str "user/jane deleted credential " name-attr ".")
                          :category           "delete"
                          :success            true
                          :linked-identifiers []
                          :authn-info         authn-info-jane
                          :acl                {:owners ["group/nuvla-admin" "user/jane"]}}))))
