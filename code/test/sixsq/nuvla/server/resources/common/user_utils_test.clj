(ns sixsq.nuvla.server.resources.common.user-utils-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-minimum :as minimum]))



(def base-uri (str p/service-context user/resource-type))


(def user-id! (atom nil))


(defn with-existing-user
  [user-email f]
  (let [validation-link (atom nil)

        session         (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
        session-admin   (header session authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")]

    (with-redefs [email-utils/extract-smtp-cfg
                                      (fn [_]
                                        {:host "smtp@example.com"
                                         :port 465
                                         :ssl  true
                                         :user "admin"
                                         :pass "password"})

                  ;; WARNING: This is a fragile!  Regex matching to recover callback URL.
                  postal/send-message (fn [_ {:keys [body]}]
                                        (let [url (->> body
                                                       (re-matches #"(?s).*link:\n\n\s+(.*?)\n.*")
                                                       (second))]
                                          (reset! validation-link url))
                                        {:code 0, :error :SUCCESS, :message "OK"})]

      (let [href          (str user-tpl/resource-type "/" minimum/registration-method)

            href-create   {:template {:href  href
                                      :email user-email}}
            user-response (-> session-admin
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str href-create))
                              (ltu/is-status 201))
            user-href     (ltu/location user-response)]
        (reset! user-id! user-href)
        (f)
        (-> session-admin
            (request (ltu/location-url user-response) :request-method :delete)
            (ltu/is-status 200))
        (reset! user-id! nil)))))
