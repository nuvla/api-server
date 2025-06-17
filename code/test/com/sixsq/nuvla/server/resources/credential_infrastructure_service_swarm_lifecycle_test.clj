(ns com.sixsq.nuvla.server.resources.credential-infrastructure-service-swarm-lifecycle-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-template :as ct]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as cred-tpl]
    [com.sixsq.nuvla.server.resources.credential.encrypt-utils :as eu]
    [com.sixsq.nuvla.server.resources.job :as job]
    [com.sixsq.nuvla.server.resources.credential.encrypt-utils-test :as ceut]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)


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
                                             :key    key-value}}]

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
      (doseq [session [session-admin session-user session-anon]]
        (-> session
            (request base-uri
                     :request-method :post
                     :body (j/write-value-as-string create-import-no-href))
            (ltu/body->edn)
            (ltu/is-status 400))))

    (testing "Creating a new credential as anon will fail; expect 400 because href cannot be accessed"
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string create-import-href))
          (ltu/body->edn)
          (ltu/is-status 400)))

    (let [resp    (testing "Create a credential as a normal user"
                    (-> session-user
                        (request base-uri
                                 :request-method :post
                                 :body (j/write-value-as-string create-import-href))
                        (ltu/body->edn)
                        (ltu/is-status 201)))
          id      (ltu/body-resource-id resp)
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
        (let [job-url-filter (str job-base-uri "?filter=action='credential_check'&target-resource/href='" id "'")]
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
          (ltu/is-status 200)))))

(deftest lifecycle-encrypted
  (with-redefs [eu/ENCRYPTION-KEY ceut/key-test]
    (let [session               (-> (ltu/ring-app)
                                   session
                                   (content-type "application/json"))
         session-user          (header session authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon")

         name-attr             "name"
         description-attr      "description"
         tags-attr             ["one", "two"]

         ca-value              "my-ca-certificate"
         cert-value            "my-public-certificate"
         key-value             "my-private-key"

         parent-value          "infrastructure-service/alpha"

         href                  (str ct/resource-type "/" cred-tpl/method)

         create-import-href    {:name        name-attr
                                :description description-attr
                                :tags        tags-attr
                                :template    {:href   href
                                              :parent parent-value
                                              :ca     ca-value
                                              :cert   cert-value
                                              :key    key-value}}]


     (let [resp    (testing "Create a credential with encryption enabled as a normal user"
                     (-> session-user
                         (request base-uri
                                  :request-method :post
                                  :body (j/write-value-as-string create-import-href))
                         (ltu/body->edn)
                         (ltu/is-status 201)))
           id      (ltu/body-resource-id resp)
           uri     (-> resp
                       (ltu/location))
           abs-uri (str p/service-context uri)]

       ;; resource id and the uri (location) should be the same
       (is (= id uri))

       (testing "Ensure credential contains correct information"
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
             (ltu/is-key-value :parent parent-value)))

       (testing "Ensure credential can always be retrieved encrypted even if there is an issue"
         (with-redefs [eu/ENCRYPTION-KEY ceut/wrong-key-test]
           (-> session-user
               (request abs-uri)
               (ltu/body->edn)
               (ltu/is-status 200)
               (ltu/is-key-value :name name-attr)
               (ltu/is-key-value :description description-attr)
               (ltu/is-key-value :tags tags-attr)
               (ltu/is-key-value :ca ca-value)
               (ltu/is-key-value :cert cert-value)
               (ltu/is-key-value #(str/starts-with? % eu/encrypted-starter-indicator) :key true)
               (ltu/is-key-value :parent parent-value))))
       ))))
