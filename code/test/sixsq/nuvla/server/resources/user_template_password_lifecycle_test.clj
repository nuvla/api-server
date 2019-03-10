(ns sixsq.nuvla.server.resources.user-template-password-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.user :as user]
    [sixsq.nuvla.server.resources.user-template :as user-tpl]
    [sixsq.nuvla.server.resources.user-template-password :as user-tpl-password]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context user/resource-type))


(def user-template-base-uri (str p/service-context user-tpl/resource-type))


(deftest check-metadata
  (mdtu/check-metadata-exists (str user-tpl/resource-type "-" user-tpl-password/resource-url)))


(deftest lifecycle
  (let [instance "my-password-instance"

        template-url (str p/service-context user-tpl/resource-type "/" instance)

        session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "root ADMIN")
        session-user (header session authn-info-header "jane USER ANON")
        session-anon (header session authn-info-header "unknown ANON")]

    (let [user-tpl (->> {:instance instance
                         :group    "my-group"
                         :icon     "some-icon"
                         :order    10
                         :hidden   false}
                        (merge user-tpl-password/resource)
                        json/write-str)]

      ;; create the template itself
      (-> session-admin
          (request user-template-base-uri
                   :request-method :post
                   :body user-tpl)
          (ltu/is-status 201))

      ;; added template must be visible to all classes of users
      (doseq [session [session-anon session-user]]
        (-> session
            (request template-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-absent "delete")
            (ltu/is-operation-absent "edit")))

      (-> session-admin
          (request template-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-present "delete")
          (ltu/is-operation-present "edit")))))
