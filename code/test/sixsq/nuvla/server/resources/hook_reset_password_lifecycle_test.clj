(ns sixsq.nuvla.server.resources.hook-reset-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [ring.util.codec :as codec]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.hook :as t]
    [sixsq.nuvla.server.resources.hook-reset-password :as hrp]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.session-password-lifecycle-test :as password-test]
    [sixsq.nuvla.server.resources.session-template :as st]
    [sixsq.nuvla.server.resources.email.sending :as email-sending]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context t/resource-type "/" hrp/action))


(def session-template-base-uri (str p/service-context st/resource-type))

(deftest lifecycle

  (let [reset-link    (atom nil)
        app           (ltu/ring-app)
        session-json  (content-type (session app) "application/json")
        session-anon  (header session-json authn-info-header "user/unknown user/unknown group/nuvla-anon")
        session-admin (header session-json authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")]

    (with-redefs [email-sending/extract-smtp-cfg
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
                                          (reset! reset-link url))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [email              "jane@example.org"
            plaintext-password "JaneJane-0"
            jane-user-id       (password-test/create-user session-admin
                                                          :username "jane"
                                                          :password plaintext-password
                                                          :activated? true
                                                          :email email)
            form               {:username     email
                                :redirect-url "http://redirect.example.org"}]

        (-> session-anon
            (request base-uri
                     :request-method :post
                     :body (json/write-str form))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/message-matches
              #"An email with instructions has been sent to your email address."))

        (let [callback-url (->> @reset-link
                                codec/url-decode
                                (re-matches #".*callback=(.*?)&.*")
                                second)]
          (-> session-anon
              (request callback-url
                       :request-method :post
                       :body (json/write-str {:new-password "too-simple"}))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/message-matches #"password must contain at least.*"))

          (-> session-anon
              (request callback-url
                       :request-method :post
                       :body (json/write-str {:new-password "VeryDifficult-1"}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/message-matches
                (re-pattern (format "set password for %s successfully executed" jane-user-id))))

          (-> session-admin
              (request (str p/service-context jane-user-id)
                       :request-method :put
                       :body (json/write-str {:id    jane-user-id
                                              :state "SUSPENDED"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; suspended user should not be able to use reset password hook
          (-> session-anon
              (request callback-url
                       :request-method :post
                       :body (json/write-str {:new-password "VeryDifficult-1"}))
              (ltu/body->edn)
              (ltu/is-status 400)
              (ltu/message-matches
                (re-pattern (format "%s is not in the 'NEW' or 'ACTIVE' state" jane-user-id))))
          )))))
