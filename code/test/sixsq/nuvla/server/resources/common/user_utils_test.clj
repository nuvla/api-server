(ns sixsq.nuvla.server.resources.common.user-utils-test
  (:require
    [clojure.data.json :as json]
    [peridot.core :refer [content-type header request session]
     :rename {session session-base}]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-minimum :as minimum]))



(def base-uri (str p/service-context user/resource-type))


(def session (-> (ltu/ring-app)
                 session-base
                 (content-type "application/json")))

(def session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon"))

(def ^:dynamic *user-ids* {})


(defn create-user [user-email]
  (with-redefs
    [email-utils/extract-smtp-cfg
                         (fn [_]
                           {:host "smtp@example.com"
                            :port 465
                            :ssl  true
                            :user "admin"
                            :pass "password"})

     postal/send-message (fn [_ _]
                           {:code 0, :error :SUCCESS, :message "OK"})]

    (let [href        (str user-tpl/resource-type "/" minimum/registration-method)

          href-create {:template {:href  href
                                  :email user-email}}]
      [user-email (-> session-admin
                      (request base-uri
                               :request-method :post
                               :body (json/write-str href-create))
                      (ltu/is-status 201)
                      (ltu/location))])))

(defn with-existing-users
  [user-emails f]
  (binding [*user-ids* (->> user-emails
                            (map create-user)
                            (into {}))]
    (f)))
