(ns sixsq.nuvla.server.resources.nuvlabox-peripheral-1-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb-peripheral/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox-id "nuvlabox/some-random-uuid")


(def nuvlabox-owner "user/alpha")

(def user-beta "user/beta")


(def valid-nuvlabox {:owner nuvlabox-owner})


(def valid-peripheral {:id            (str nb-peripheral/resource-type "/uuid")
                       :resource-type nb-peripheral/resource-type
                       :name          "Webcam C920"
                       :description   "Logitech, Inc. HD Pro Webcam C920"
                       :created       timestamp
                       :updated       timestamp

                       :version       1

                       :identifier    "046d:082d"
                       :available     true
                       :device-path   "/dev/bus/usb/001/001"
                       :interface     "USB"
                       :vendor        "SixSq"
                       :product       "HD Pro Webcam C920"
                       :classes       ["AUDIO" "VIDEO"]})


(deftest check-metadata
  (mdtu/check-metadata-exists nb-peripheral/resource-type))


(deftest lifecycle

  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-user  (header session authn-info-header (str user-beta " group/nuvla-user group/nuvla-anon"))
        session-owner (header session authn-info-header (str nuvlabox-owner " group/nuvla-user group/nuvla-anon"))
        session-anon  (header session authn-info-header "user/unknown group/nuvla-anon")

        nuvlabox-id   (-> session-user
                          (request nuvlabox-base-uri
                                   :request-method :post
                                   :body (json/write-str valid-nuvlabox))
                          (ltu/body->edn)
                          (ltu/is-status 201)
                          (ltu/location))

        session-nb    (header session authn-info-header (str nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

    ;; anonymous users cannot create a nuvlabox-peripheral resource
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-peripheral :parent nuvlabox-id)))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; admin/nuvlabox users can create a nuvlabox-peripheral resource
    ;; use the default ACL
    (when-let [peripheral-url (-> session-nb
                                  (request base-uri
                                           :request-method :post
                                           :body (json/write-str (assoc valid-peripheral
                                                                   :parent nuvlabox-id)))
                                  (ltu/body->edn)
                                  (ltu/is-status 201)
                                  (ltu/location-url))]

      ;; other users can't see the peripheral
      (-> session-user
          (request peripheral-url)
          (ltu/body->edn)
          (ltu/is-status 403))

      ;; owners can see the peripheral
      (-> session-owner
          (request peripheral-url)
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; nuvlabox user is able to update nuvlabox-peripheral
      (-> session-nb
          (request peripheral-url
                   :request-method :put
                   :body (json/write-str {:interface "BLUETOOTH"}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :interface "BLUETOOTH"))

      ;; verify that the update was written to disk
      (-> session-nb
          (request peripheral-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :interface "BLUETOOTH"))

      ;; nuvlabox owner identity cannot delete the peripheral
      (-> session-owner
          (request peripheral-url
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
          (ltu/is-status 200)
          (ltu/location))

      ;; now user beta can also see the peripheral
      (-> session-user
          (request peripheral-url)
          (ltu/body->edn)
          (ltu/is-status 200))



      ;; nuvlabox can delete the peripheral
      (-> session-nb
          (request peripheral-url
                   :request-method :delete)
          (ltu/body->edn)
          (ltu/is-status 200)))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-peripheral/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
