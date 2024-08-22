(ns com.sixsq.nuvla.server.resources.nuvlabox-playbook-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.nuvlabox-playbook :as nb-playbook]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb-playbook/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox-id "nuvlabox/some-random-uuid")


(def nuvlabox-owner "user/alpha")

(def user-beta "user/beta")


(def valid-nuvlabox {:owner nuvlabox-owner})


(def valid-playbook {:id            (str nb-playbook/resource-type "/uuid")
                     :resource-type nb-playbook/resource-type
                     :name          "my nuvlabox-playbook"
                     :description   "description of my nuvlabox-playbook"
                     :created       timestamp
                     :updated       timestamp
                     :type          "EMERGENCY"
                     :run           "echo hello world"
                     :enabled       true})


(deftest check-metadata
  (mdtu/check-metadata-exists nb-playbook/resource-type))


(deftest lifecycle

  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-user  (header session authn-info-header (str user-beta " " user-beta " group/nuvla-user group/nuvla-anon"))
          session-owner (header session authn-info-header (str nuvlabox-owner " " nuvlabox-owner " group/nuvla-user group/nuvla-anon"))
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

          nuvlabox-id   (-> session-owner
                            (request nuvlabox-base-uri
                                     :request-method :post
                                     :body (json/write-str valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          session-nb    (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

      ;; anonymous users cannot create a nuvlabox-playbook resource
      (-> session-anon
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-playbook :parent nuvlabox-id)))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; users who cannot edit a NB cannot create a playbook for it
      (-> session-user
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-playbook :parent nuvlabox-id)))
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; missing parent causes a 400
      (-> session-owner
          (request base-uri
                   :request-method :post
                   :body (json/write-str {}))
          (ltu/body->edn)
          (ltu/is-status 400))

      ;; nuvlabox owners can create a nuvlabox-playbook resource
      ;; use the default ACL
      (when-let [playbook-url (-> session-owner
                                  (request base-uri
                                           :request-method :post
                                           :body (json/write-str (assoc valid-playbook
                                                                   :parent nuvlabox-id)))
                                  (ltu/body->edn)
                                  (ltu/is-status 201)
                                  (ltu/location-url))]

        ;; other users can't see the playbook
        (-> session-user
            (request playbook-url)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; owners can see the playbook
        (-> session-owner
            (request playbook-url)
            (ltu/body->edn)
            (ltu/is-operation-present :save-output)
            (ltu/is-status 200))

        ;; nuvlabox user can see the playbook
        (-> session-nb
            (request playbook-url)
            (ltu/body->edn)
            (ltu/is-operation-present :save-output)
            (ltu/is-status 200))

        ;; nuvlabox user is not able to update nuvlabox-playbook
        (-> session-nb
            (request playbook-url
                     :request-method :put
                     :body (json/write-str {:output "new output"}))
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; owner is able to update nuvlabox-playbook
        (-> session-owner
            (request playbook-url
                     :request-method :put
                     :body (json/write-str {:output "new output"}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :output "new output"))

        ;; verify that the update was written to disk
        (-> session-nb
            (request playbook-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :output "new output"))

        ;; nuvlabox identity cannot delete the playbook
        (-> session-nb
            (request playbook-url
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; share nuvlabox with user beta
        (-> session-owner
            (request (str p/service-context nuvlabox-id)
                     :request-method :put
                     :body (json/write-str {:acl {:owners   ["group/nuvla-admin"]
                                                  :view-acl [nuvlabox-owner user-beta]}}))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; now user beta can also see the playbook
        (-> session-user
            (request playbook-url)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; save-output operation tests
        (let [save-output-op-url (-> session-owner
                                     (request playbook-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :save-output)
                                     (ltu/get-op-url :save-output))]
          ;; the nb can save new output through the save-output op
          (-> session-nb
              (request save-output-op-url
                       :request-method :post
                       :body (json/write-str {:output "newest stdout"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; confirm the op saved the output
          (-> session-nb
              (request playbook-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :output "newest stdout\n\nnew output"))

          ;; output saved via the op must be truncated
          (-> session-nb
              (request save-output-op-url
                       :request-method :post
                       :body (json/write-str {:output (apply str (repeat 12500 "g"))}))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-nb
              (request playbook-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value count :output 10000)))

        ;; very long outputs get truncated via edit as well
        (-> session-owner
            (request playbook-url
                     :request-method :put
                     :body (json/write-str {:output (apply str (repeat 10500 "f"))}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value count :output 10000))

        ;; nuvlabox owner can delete the playbook
        (-> session-owner
            (request playbook-url
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-playbook/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
