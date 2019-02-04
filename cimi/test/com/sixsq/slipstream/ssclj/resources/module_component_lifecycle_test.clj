(ns com.sixsq.slipstream.ssclj.resources.module-component-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is use-fixtures]]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.slipstream.ssclj.resources.module-component :as module-component]
    [peridot.core :refer :all]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context (u/de-camelcase module-component/resource-name)))


(def valid-acl {:owner {:type      "ROLE"
                        :principal "ADMIN"}
                :rules [{:principal "ADMIN"
                         :right     "ALL"
                         :type      "ROLE"}]})


(def timestamp "1964-08-25T10:00:00.0Z")


(def valid-entry {:id               (str module-component/resource-url "/connector-uuid")
                  :resourceURI      module-component/resource-uri
                  :created          timestamp
                  :updated          timestamp
                  :acl              valid-acl

                  :parentModule     {:href "module/my-parent-module"}

                  :cpu              2
                  :ram              2048
                  :disk             100
                  :volatileDisk     500
                  :networkType      "public"

                  :inputParameters  [{:parameter "iparam-1" :description "desc2" :value "100"}
                                     {:parameter "iparam-2" :description "desc2"}
                                     {:parameter "iparam-3"}]

                  :outputParameters [{:parameter "iparam-1" :description "desc2" :value "100"}
                                     {:parameter "iparam-2" :description "desc2"}
                                     {:parameter "iparam-3"}]

                  :targets          {:preinstall  "preinstall"
                                     :packages    ["emacs-nox" "vim"]
                                     :postinstall "postinstall"
                                     :deployment  "deployment"
                                     :reporting   "reporting"
                                     :onVmAdd     "onVmAdd"
                                     :onVmRemove  "onVmRemove"
                                     :prescale    "prescale"
                                     :postscale   "postscale"}
                  :author           "someone"
                  :commit           "wip"
                  })


(deftest lifecycle

  (let [session-anon (-> (session (ltu/ring-app))
                         (content-type "application/json"))
        session-admin (header session-anon authn-info-header "super ADMIN USER ANON")
        session-user (header session-anon authn-info-header "jane USER ANON")]

    ;; create: NOK for anon, users
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-entry))
          (ltu/body->edn)
          (ltu/is-status 403)))

    ;; queries: OK for admin, NOK for others
    (doseq [session [session-anon session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 403)))

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 0))



    ;; adding, retrieving and  deleting entry as user should succeed
    (let [uri (-> session-admin
                  (request base-uri
                           :request-method :post
                           :body (json/write-str valid-entry))
                  (ltu/body->edn)
                  (ltu/is-status 201)
                  (ltu/location))

          abs-uri (str p/service-context (u/de-camelcase uri))]

      ;; retrieve: OK for admin; NOK for others
      (doseq [session [session-anon session-user]]
        (-> session
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403)))

      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; delete: OK for admin; NOK for others
      (doseq [session [session-anon session-user]]
        (-> session
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403)))

      (-> session-admin
          (request abs-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; verify that the resource was deleted.
      (-> session-admin
          (request abs-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id module-component/resource-name))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
