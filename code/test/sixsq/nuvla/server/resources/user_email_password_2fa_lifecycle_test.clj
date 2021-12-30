(ns sixsq.nuvla.server.resources.user-email-password-2fa-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-email-password :as email-password]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" email-password/resource-url)))


(deftest lifecycle
  (let [validation-link (atom nil)
        session         (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
        session-admin   (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-anon    (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

    (with-redefs [email-utils/extract-smtp-cfg
                                      (fn [_] {:host "smtp@example.com"
                                               :port 465
                                               :ssl  true
                                               :user "admin"
                                               :pass "password"})

                  ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body]}]
                                        (let [url (->> body second :content
                                                       (re-matches #"(?s).*visit:\n\n\s+(.*?)\n.*")
                                                       second)]
                                          (reset! validation-link url))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [href               (str user-tpl/resource-type "/" email-password/registration-method)

            description-attr   "description"
            tags-attr          ["one", "two"]
            plaintext-password "Plaintext-password-1"

            href-create        {:description description-attr
                                :tags        tags-attr
                                :template    {:href     href
                                              :password plaintext-password
                                              ;:username "user/jane"
                                              :email    "jane@example.org"}}]

        ;; create user
        (let [resp                 (-> session-anon
                                       (request base-uri
                                                :request-method :post
                                                :body (json/write-str href-create))
                                       (ltu/body->edn)
                                       (ltu/is-status 201))
              user-id              (ltu/body-resource-id resp)
              session-created-user (header session authn-info-header (str user-id " " user-id " group/nuvla-user group/nuvla-anon"))]

          (let [enable-2fa-url (-> session-created-user
                                   (request (str p/service-context user-id))
                                   (ltu/body->edn)
                                   (ltu/is-operation-present :enable-2fa)
                                   (ltu/is-operation-absent :disable-2fa)
                                   (ltu/get-op-url :enable-2fa))
                callback-url (-> session-created-user
                                 (request enable-2fa-url
                                          :request-method :post
                                          :body (json/write-str {:token "afv"
                                                                 :method "eemail"}))
                                 (ltu/body->edn)
                                 (ltu/is-status 403)
                                 #_(ltu/dump)
                                 #_(ltu/location-url))]
            )

          ;;; check validation of resource
          ;(is (not (nil? @validation-link)))
          ;
          ;(-> session-admin
          ;    (request (str p/service-context user-id))
          ;    (ltu/body->edn)
          ;    (ltu/is-status 200))
          ;
          ;(is (re-matches #"^email.*successfully validated$" (-> session-anon
          ;                                                       (request @validation-link)
          ;                                                       (ltu/body->edn)
          ;                                                       (ltu/is-status 200)
          ;                                                       (ltu/body)
          ;                                                       :message)))
          ;
          ;(let [{:keys [state]} (-> session-created-user
          ;                          (request (str p/service-context user-id))
          ;                          (ltu/body->edn)
          ;                          (ltu/body))]
          ;  (is (= "ACTIVE" state)))
          ;
          ;(-> session-created-user
          ;    (request (str p/service-context user-id)
          ;             :request-method :delete)
          ;    (ltu/body->edn)
          ;    (ltu/is-status 200))

          )))))

