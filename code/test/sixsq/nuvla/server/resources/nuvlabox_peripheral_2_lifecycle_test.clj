(ns sixsq.nuvla.server.resources.nuvlabox-peripheral-2-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
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


(def valid-peripheral {:id                          (str nb-peripheral/resource-type "/uuid")
                       :resource-type               nb-peripheral/resource-type
                       :name                        "Webcam C920"
                       :description                 "Logitech, Inc. HD Pro Webcam C920"
                       :created                     timestamp
                       :updated                     timestamp

                       :version                     2

                       :identifier                  "046d:082d"
                       :available                   true
                       :device-path                 "/dev/bus/usb/001/001"
                       :interface                   "USB"
                       :port                        1
                       :vendor                      "SixSq"
                       :product                     "HD Pro Webcam C920"
                       :classes                     ["AUDIO" "VIDEO"]
                       :raw-data-sample             "{\"datapoint\": 1, \"value\": 2}"
                       :local-data-gateway-endpoint "data-gateway/video/1"
                       :data-gateway-enabled        false
                       :serial-number               "123456"
                       :video-device                "/dev/video0"
                       :additional-assets           {:devices     ["/dev/device1", "/dev/device2"]
                                                     :libraries   ["/lib/a", "/lib/b"]}
                       :resources                   [{:unit "cuda cores"
                                                      :capacity "100"
                                                      :load 50}]})


(deftest check-metadata
  (mdtu/check-metadata-exists nb-peripheral/resource-type))


(deftest lifecycle

  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                           session
                           (content-type "application/json"))
         session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
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
           ; these are disabled on version 2
           (ltu/is-operation-absent :enable-stream)
           (ltu/is-operation-absent :disable-stream)
           (ltu/is-status 200))

       ;; nuvlabox user is able to update nuvlabox-peripheral
       (-> session-nb
           (request peripheral-url
                    :request-method :put
                    :body (json/write-str {:interface "BLUETOOTH"}))
           (ltu/body->edn)
           (ltu/is-status 200)
           (ltu/is-operation-absent :enable-stream)
           (ltu/is-operation-absent :disable-stream)
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
           (ltu/is-status 200))

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
           (ltu/is-status 200)))


     (when-let [peripheral-url (-> session-nb
                                   (request base-uri
                                            :request-method :post
                                            :body (json/write-str (assoc valid-peripheral
                                                                    :parent nuvlabox-id)))
                                   (ltu/body->edn)
                                   (ltu/is-status 201)
                                   (ltu/location-url))]

       ;; nuvlabox can delete the peripheral
       (-> session-admin
           (request peripheral-url
                    :request-method :delete)
           (ltu/body->edn)
           (ltu/is-status 200)))
     )))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-peripheral/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
