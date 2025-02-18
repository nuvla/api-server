(ns com.sixsq.nuvla.server.resources.common.resource-creation
  (:require
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-template :as ct]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as cred-tpl]
    [com.sixsq.nuvla.server.resources.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.email.sending :as email-sending]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.module :as module]
    [com.sixsq.nuvla.server.resources.module-application :as module-application]
    [com.sixsq.nuvla.server.resources.module-application-helm :as module-application-helm]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nuvlabox]
    [com.sixsq.nuvla.server.resources.user :as user]
    [com.sixsq.nuvla.server.resources.user-template :as user-tpl]
    [com.sixsq.nuvla.server.resources.user-template-minimum :as minimum]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]
     :rename {session session-base}]
    [postal.core :as postal]))


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
                   :body (j/write-value-as-string {:template {:href  (str user-tpl/resource-type "/" minimum/registration-method)
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

(def valid-helm-module
  {:id                        (str module/resource-type "/helm-component-uuid")
   :resource-type             module/resource-type
   :created                   timestamp
   :updated                   timestamp
   :path                      "a/b"
   :subtype                   "application_helm"

   :data-accept-content-types ["application/json" "application/x-something"]
   :data-access-protocols     ["http+s3" "posix+nfs"]

   :content                   {:id             (str module-application-helm/resource-type
                                                    "/module-application-helm-uuid")
                               :resource-type  module-application-helm/resource-type
                               :created        timestamp
                               :updated        timestamp
                               :acl            {:owners   ["group/nuvla-admin"]
                                                :view-acl ["user/jane"]}

                               :author         "someone"
                               :commit         "wip"

                               :helm-repo-url   "infrastructure-service/uuid-helm-repo"
                               :helm-chart-name "hello-world"}})

(defn create-module
  ([session]
   (create-module session "a" "a/b"))
  ([session project-path module-path]
   (create-module session project-path module-path valid-module))
  ([session project-path module-path payload]
   (binding [config-nuvla/*stripe-api-key* nil]
     (-> session
         (request (str p/service-context module/resource-type)
                  :request-method :post
                  :body (j/write-value-as-string
                          {:subtype "project"
                           :path    project-path}))
         (ltu/body->edn)
         (ltu/is-status 201))
     (-> session
         (request (str p/service-context module/resource-type)
                  :request-method :post
                  :body (j/write-value-as-string (assoc payload :path module-path)))
         (ltu/body->edn)
         (ltu/is-status 201)
         (ltu/location)))))


(defn create-deployment
  [session module-id]
  (binding [config-nuvla/*stripe-api-key* nil]
    (-> session
        (request (str p/service-context deployment/resource-type)
                 :request-method :post
                 :body (j/write-value-as-string {:module {:href module-id}}))
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
                 :body (j/write-value-as-string tmpl))
        (ltu/body->edn)
        (ltu/is-status 201)
        (ltu/location))))

(defn create-nuvlabox
  [session data]
  (-> session
      (request (str p/service-context nuvlabox/resource-type)
               :request-method :post
               :body (j/write-value-as-string data))
      (ltu/body->edn)
      (ltu/is-status 201)
      (ltu/location)))
