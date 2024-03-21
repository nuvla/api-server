(ns sixsq.nuvla.server.resources.common.resource-creation
  (:require
    [clojure.data.json :as json]
    [peridot.core :refer [content-type header request session]
     :rename {session session-base}]
    [postal.core :as postal]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template :as ct]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as cred-tpl]
    [sixsq.nuvla.server.resources.deployment :as deployment]
    [sixsq.nuvla.server.resources.email.sending :as email-sending]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.resources.module-application :as module-application]
    [sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-minimum :as minimum]))


(defn create-user [user-email]
  (with-redefs [email-sending/extract-smtp-cfg
                                    (fn [_]
                                      {:host "smtp@example.com"
                                       :port 465
                                       :ssl  true
                                       :user "admin"
                                       :pass "password"})

                postal/send-message (fn [_ _]
                                      {:code 0, :error :SUCCESS, :message "OK"})]

    (let [session (-> (ltu/ring-app)
                      session-base
                      (content-type "application/json"))]
      (-> (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          (request (str p/service-context user/resource-type)
                   :request-method :post
                   :body (json/write-str {:template {:href  (str user-tpl/resource-type "/" minimum/registration-method)
                                                     :email user-email}}))
          (ltu/is-status 201)
          (ltu/location)))))


(def timestamp "1964-08-25T10:00:00.00Z")


(def valid-module
  {:id                        (str module/resource-type "/component-uuid")
   :resource-type             module/resource-type
   :created                   timestamp
   :updated                   timestamp
   :path                      "a/b"
   :subtype                   "application"
   :compatibility             "docker-compose"

   :logo-url                  "https://example.org/logo"

   :data-accept-content-types ["application/json" "application/x-something"]
   :data-access-protocols     ["http+s3" "posix+nfs"]

   :content                   {:id             (str module-application/resource-type
                                                    "/module-application-uuid")
                               :resource-type  module-application/resource-type
                               :created        timestamp
                               :updated        timestamp
                               :acl            {:owners   ["group/nuvla-admin"]
                                                :view-acl ["user/jane"]}

                               :author         "someone"
                               :commit         "wip"

                               :docker-compose "version: \"3.3\"\nservices:\n  web:\n    ..."}})


(defn create-module
  [session]
  (binding [config-nuvla/*stripe-api-key* nil]
    (-> session
        (request (str p/service-context module/resource-type)
                 :request-method :post
                 :body (json/write-str
                         {:subtype "project"
                          :path    "a"}))
        (ltu/body->edn)
        (ltu/is-status 201))
    (-> session
        (request (str p/service-context module/resource-type)
                 :request-method :post
                 :body (json/write-str valid-module))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))))


(defn create-deployment
  [session module-id]
  (binding [config-nuvla/*stripe-api-key* nil]
    (-> session
        (request (str p/service-context deployment/resource-type)
                 :request-method :post
                 :body (json/write-str {:module {:href module-id}}))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))))

(defn create-credential-swarm
  [session data]
  (let [default-href (str ct/resource-type "/" cred-tpl/method)
        tmpl         (merge-with merge
                                 data
                                 {:template {:href default-href
                                             :ca   "ca value"
                                             :cert "cert value"
                                             :key  "key value"}})]
    (-> session
        (request (str p/service-context credential/resource-type)
                 :request-method :post
                 :body (json/write-str tmpl))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))))

(defn create-nuvlabox
  [session data]
  (-> session
      (request (str p/service-context nuvlabox/resource-type)
               :request-method :post
               :body (json/write-str data))
      (ltu/body->edn)
      (ltu/is-status 201)
      (ltu/location)))
