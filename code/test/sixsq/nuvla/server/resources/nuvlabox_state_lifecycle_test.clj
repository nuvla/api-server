(ns sixsq.nuvla.server.resources.nuvlabox-state-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox-record :as nb]
    [sixsq.nuvla.server.resources.nuvlabox-state :refer :all]
    [sixsq.nuvla.server.resources.nuvlabox-state-snapshot :as nbss]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [peridot.core :refer :all]
    [clojure.tools.logging :as log]))

(use-fixtures :each ltu/with-test-server-fixture)

(def nuvlabox-base-uri (str p/service-context nb/resource-type))

(def state-snapshot-base-uri (str p/service-context nbss/resource-type))

(def base-uri (str p/service-context resource-type))

(def timestamp "1964-08-25T10:00:00Z")

(def valid-nuvlabox {:created      timestamp
                     :updated      timestamp
                     :acl          {:owners   ["group/nuvla-admin"]
                                    :view-acl ["user/jane"]}
                     :identifier   "Albert Einstein"
                     :connector    {:href "connector/nuvlabox-albert-einstein"}
                     :macAddress   "aa:bb:cc:dd:ee:ff"
                     :owner        {:href "test"}
                     :organization "org"
                     :sslCA        "ssl ca"
                     :sslCert      "ssl cert"
                     :sslKey       "ssl key"
                     :vmCidr       "10.0.0.0/24"
                     :lanCidr      "10.0.1.0/24"
                     :vpnIP        "10.0.0.2"})

(def usb1 {:busy        false
           :vendor-id   "vendor-id"
           :device-id   "device-id-1"
           :bus-id      "bus-id"
           :product-id  "product-id"
           :description "descr"})

(def usb2 {:busy        false
           :vendor-id   "vendor-id"
           :device-id   "device-id-1"
           :bus-id      "bus-id"
           :product-id  "product-id"
           :description "descr"})

(def ram {:capacity 4000
          :used     2000})

(def ram-updated {:capacity 4000
                  :used     3000})


(deftest lifecycle

  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-jane (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon (header session authn-info-header "user/unknown group/nuvla-anon")]

    ;; non admin Users cannot create nuvlabox
    ;; FIXME: Seems to be an inconsistency between nuvlabox-record tests and this test; for the other users can create nuvlaboxes
    #_(-> session-jane
          (request nuvlabox-base-uri
                   :request-method :post
                   :body (json/write-str valid-nuvlabox))
          (ltu/body->edn)
          (ltu/is-status 403))

    ;; Admin can create nuvlabox-record, and does not need quota
    (-> session-admin
        (request nuvlabox-base-uri
                 :request-method :post
                 :body (json/write-str valid-nuvlabox))
        (ltu/body->edn)
        (ltu/is-status 201))


    (with-redefs [utils/call-vpn-api (fn [mock-vpn-ip _ _ _]
                                       [201 {:sslCA   "sslCA"
                                             :sslCert "sslCert"
                                             :sslKey  "sslKey"
                                             :vmCidr  "vm CIDR"
                                             :vpnIP   mock-vpn-ip}])]

      (let [resp-admin (-> session-admin
                           (request nuvlabox-base-uri
                                    :request-method :post
                                    :body (json/write-str (assoc valid-nuvlabox :macAddress "01:01:01:01:01")))
                           (ltu/body->edn)
                           (ltu/is-status 201))

            id-nuvlabox (get-in resp-admin [:response :body :resource-id])
            uri-nuvlabox (str p/service-context id-nuvlabox)
            new-nuvlabox (-> session-admin
                             (request uri-nuvlabox)
                             (ltu/body->edn)
                             (ltu/is-status 200))

            activate-url-action (str p/service-context (ltu/get-op new-nuvlabox "activate"))

            ;; create namespace (required by service offer creation)
            valid-namespace {:prefix "schema-org"
                             :uri    "https://schema-org/a/b/c.md"}

            _ (-> session-admin
                  (request (str p/service-context sn/resource-type)
                           :request-method :post
                           :body (json/write-str valid-namespace))
                  (ltu/body->edn)
                  (ltu/is-status 201))

            username (-> session-anon
                         (request activate-url-action :request-method :post)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (get-in [:response :body :username]))

            session-nuvlabox-user (header session authn-info-header (str username " group/nuvla-user group/nuvla-anon"))

            ;; activate nuvlabox must create a nuvlabox-state entry
            nuvlabox-state-id (-> session-nuvlabox-user
                                  (request base-uri)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-count 1)
                                  (get-in [:response :body :resources])
                                  first
                                  :id)
            nuvlabox-state-href (str p/service-context nuvlabox-state-id)]

        ;; nuvlabox user is able to update nuvlabox-state
        (-> session-admin #_session-nuvlabox-user
            (request nuvlabox-state-href
                     :request-method :put
                     :body (json/write-str {:usb [usb1]
                                            :ram ram}))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; each update create a nuvlabox-state-snapshot
        (-> session-nuvlabox-user
            (request state-snapshot-base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 1))

        (-> session-nuvlabox-user
            (request nuvlabox-state-href
                     :request-method :put
                     :body (json/write-str {:ram ram-updated}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :ram ram-updated))

        (-> session-nuvlabox-user
            (request state-snapshot-base-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-count 2))

        ;; ram, cpu, usb and disk test update

        (-> session-nuvlabox-user
            (request nuvlabox-state-href
                     :request-method :put
                     :body (json/write-str {:usb [usb1]}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :usb [usb1]))

        ;; usb if not present in update request will stay the same
        (-> session-nuvlabox-user
            (request nuvlabox-state-href
                     :request-method :put
                     :body (json/write-str {}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :usb [usb1]))

        ;; update the usb value
        (-> session-nuvlabox-user
            (request nuvlabox-state-href
                     :request-method :put
                     :body (json/write-str {:usb [usb1 usb2]}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :usb [usb1 usb2]))

        (-> session-nuvlabox-user
            (request nuvlabox-state-href
                     :request-method :put
                     :body (json/write-str {:usb [usb2]}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :usb [usb2]))

        (-> session-nuvlabox-user
            (request nuvlabox-state-href
                     :request-method :put
                     :body (json/write-str {}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :usb [usb2]))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id resource-type))]
    (doall
      (for [[uri method] [[nuvlabox-base-uri :options]
                          [nuvlabox-base-uri :delete]
                          [resource-uri :options]
                          [resource-uri :post]]]
        (do
          (-> (session (ltu/ring-app))
              (request uri
                       :request-method method
                       :body (json/write-str {:dummy "value"}))
              (ltu/is-status 405)))))))
