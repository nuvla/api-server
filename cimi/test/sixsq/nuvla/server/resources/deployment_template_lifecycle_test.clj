(ns sixsq.nuvla.server.resources.deployment-template-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is use-fixtures]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.deployment-template :as dt]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.resources.module-lifecycle-test :as module-test]
    [peridot.core :refer :all]))


(use-fixtures :each ltu/with-test-server-fixture)


(def collection-uri (str p/service-context (u/de-camelcase dt/resource-name)))

(defn valid-comp [image-id] {:parentModule     {:href image-id}

                             :networkType      "public"

                             :disk             200

                             :inputParameters  [{:parameter "iparam-1" :description "desc2" :value "100"}
                                                {:parameter "iparam-2" :description "desc2"}
                                                {:parameter "iparam-3"}]

                             :outputParameters [{:parameter "oparam-1" :description "desc2" :value "100"}
                                                {:parameter "oparam-2" :description "desc2"}
                                                {:parameter "oparam-3"}]

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
                             :commit           "wip"})

(deftest lifecycle

  (let [session-anon (-> (ltu/ring-app)
                         session
                         (content-type "application/json"))
        session-user (header session-anon authn-info-header "jane USER")
        session-tarzan (header session-anon authn-info-header "tarzan USER")
        session-admin (header session-anon authn-info-header "root ADMIN")

        module-img-uri (-> session-user
                           (request module-test/base-uri
                                    :request-method :post
                                    :body (json/write-str (assoc module-test/valid-entry
                                                            :content module-test/valid-image)))
                           (ltu/body->edn)
                           (ltu/is-status 201)
                           (ltu/location))

        module-uri (-> session-user
                       (request module-test/base-uri
                                :request-method :post
                                :body (json/write-str (assoc {:resourceURI module/resource-uri
                                                              :parentPath  "a/b"
                                                              :path        "a/b/c"
                                                              :type        "COMPONENT"}
                                                        :content (valid-comp module-img-uri))))
                       (ltu/body->edn)
                       (ltu/is-status 201)
                       (ltu/location))

        valid-create {:module {:href module-uri}}]

    (-> session-user
        (request (str p/service-context module-uri))
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; anonymous create should fail
    (-> session-anon
        (request collection-uri
                 :request-method :post
                 :body (json/write-str valid-create))
        (ltu/body->edn)
        (ltu/is-status 403))


    ;; full deployment template lifecycle as user should work
    (let [deployment-template-uri (-> session-user
                                      (request collection-uri
                                               :request-method :post
                                               :body (json/write-str valid-create))
                                      (ltu/body->edn)
                                      (ltu/is-status 201)
                                      (ltu/location))
          resource-uri (str p/service-context (u/de-camelcase deployment-template-uri))]

      ;; admin get succeeds
      (-> session-admin
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user get succeeds
      (-> session-user
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value #(-> % :module :path) (:path module-test/valid-entry)))

      ;; user tarzan get fails
      (-> session-tarzan
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; user update works
      (-> session-user
          (request resource-uri
                   :request-method :put
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; user query succeeds
      (-> session-user
          (request collection-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count 1))

      ;; anonymous query fails
      (-> session-anon
          (request collection-uri)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; user delete succeeds
      (-> session-user
          (request resource-uri
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; ensure entry is really gone
      (-> session-admin
          (request resource-uri)
          (ltu/body->edn)
          (ltu/is-status 404)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id dt/resource-name))]
    (ltu/verify-405-status [[collection-uri :options]
                            [collection-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
