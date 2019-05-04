(ns sixsq.nuvla.server.resources.nuvlabox-record-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox-record :as nb]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [peridot.core :refer :all]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context nb/resource-type))
(def timestamp "1964-08-25T10:00:00Z")
(def connector-name1 "nuvlabox-test")
(def user "jane")

(defn random-mac-address []
  (let [random-str (apply str (remove #((set "-") %) (u/random-uuid)))]
    (->> random-str
         (partition 2)
         (take 6)
         (map #(apply str %))
         (interpose ":")
         (apply str))))

(def valid-nuvlabox {:created      timestamp
                     :updated      timestamp
                     :acl          {:owners   ["group/nuvla-admin"]
                                    :view-acl ["group/nuvla-user"]}
                     :organization "ACME"
                     :connector    {:href (str "connector/" connector-name1)}
                     :formFactor   "Nuvlabox"
                     :macAddress   "aa:bb:cc:dd:ee:ff"
                     :owner        {:href "test"}
                     :vmCidr       "10.0.0.0/24"
                     :lanCidr      "10.0.1.0/24"
                     :vpnIP        "10.0.0.2"})


(def valid-nano {:created        timestamp
                 :updated        timestamp
                 :macAddress     "gg:hh:ii:jj:kk:ll"
                 :owner          {:href "user/test"}
                 :OSVersion      "OS version"
                 :hwRevisionCode "a020d3"
                 :loginUsername  "aLoginName"
                 :loginPassword  "aLoginPassword"
                 :formFactor     "Nano"
                 :organization   "nanoland"
                 :CPU            4
                 :RAM            976})

(defn random-nano
  ([] (random-nano "a-nb-owner"))
  ([owner-name]
   (assoc valid-nano :macAddress (random-mac-address)
                     :organization "randomorg"
                     :owner {:href (str "user/" owner-name)})))


(def sample-json-response "{\"sslCA\":\"-----BEGIN CERTIFICATE-----\\ntest==\\n-----END CERTIFICATE-----\",\"sslCert\":\"-----BEGIN CERTIFICATE-----\\ntestCertY=\\n-----END CERTIFICATE-----\",\"sslKey\":\"-----BEGIN PRIVATE KEY-----\\nprivate==\\n-----END PRIVATE KEY-----\\n\",\"vpnIP\":\"10.0.128.13\"}")
(def mock-vpn-ip "10.1.42.42")

(def sample-response-map (-> sample-json-response
                             (json/read-str :key-fn keyword)
                             (assoc :vpnIP mock-vpn-ip)))



(deftest lifecycle
  (let [session (-> (ltu/ring-app)
                    session
                    (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        session-jane (header session authn-info-header "user/jane group/nuvla-user group/nuvla-anon")
        session-anon (header session authn-info-header "unknown group/nuvla-anon")]

    ;; admin and deployer collection query should succeed but be empty (no  records created yet)


    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count zero?)
        (ltu/is-operation-present "add")
        (ltu/is-operation-absent "delete")
        (ltu/is-operation-absent "edit"))


    ;; normal collection query should succeed
    (-> session-jane
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200))

    ;; anonymous collection query should not succeed
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))


    ;;In case the VPN API call is called with incomplete information
    ;; (e.g missing organization attribute), record should not be created
    (with-redefs [utils/call-vpn-api (fn [_ _ _ _] [412 "Incomplete Nuvlabox record attributes"])]
      (doseq [entry #{(dissoc valid-nuvlabox :organization) (dissoc valid-nano :organization)}]
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str entry))
            (ltu/body->edn)
            (ltu/is-status 412))))

    ;;In case the record is complete but VPN API call is failing, record should not be created
    ;;redefs   to avoid waiting for the timeout
    (with-redefs [utils/call-vpn-api (fn [mock-vpn-ip _ _ _] [503 "error in VPN API"])]
      (doseq [entry #{valid-nuvlabox
                      valid-nano}]
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str entry))
            (ltu/body->edn)
            (ltu/is-status 503))))


    ;;Any non 201 error from VPN API should be reported
    (with-redefs [utils/call-vpn-api (fn [mock-vpn-ip _ _ _] [400 "an error"])]
      (doseq [entry #{(assoc valid-nuvlabox :organization "org")
                      (assoc valid-nano :organization "org")}]
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str (assoc entry :organization "org")))
            (ltu/body->edn)
            (ltu/is-status 400))))


    ;; Admin does not require quota for creation
    (with-redefs [utils/call-vpn-api (fn [_ _ _ _] [201 sample-response-map])]
      (doseq [entry #{valid-nuvlabox valid-nano}]
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str (assoc entry :macAddress (random-mac-address))))
            (ltu/body->edn)
            (ltu/is-status 201))))


    ;; Need some time for complete removal of the nuvlabox-records
    (Thread/sleep 2000)

    ;;incomplete record should have been deleted when vpn API did not succeed
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 2))

    ;; creating a nuvlabox as a normal user should succeed; both nano and regular
    (with-redefs [utils/call-vpn-api (fn [_ _ _ _] [201 sample-response-map])]

      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-nuvlabox))
          (ltu/body->edn)
          (ltu/is-status 201)))

    (with-redefs [utils/call-vpn-api (fn [_ _ _ _] [201 sample-response-map])]
      (-> session-jane
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-nano))
          (ltu/body->edn)
          (ltu/is-status 201)))

    (with-redefs [utils/call-vpn-api (fn [_ _ _ _] [201 sample-response-map])]
      (let [nb1 (random-nano "owner1")
            nb2 (random-nano "owner2")]


        ;;admin creates nuvlaboxes

        (doseq [nb #{nb1 nb2}]
          (-> session-admin
              (request base-uri
                       :request-method :post
                       :body (json/write-str nb))
              (ltu/body->edn)
              (ltu/is-status 201)))

        ;;user can not re-create with same macAddress
        (doseq [nb #{nb1 nb2}]
          (-> session-jane
              (request base-uri
                       :request-method :post
                       :body (json/write-str nb))
              (ltu/body->edn)
              (ltu/is-status 409)))


        ;;same owners, different macAdresses : quota should not be reached yet
        (doseq [nb #{(assoc nb1 :macAddress (random-mac-address)) (assoc nb2 :macAddress (random-mac-address))}]
          (-> session-jane
              (request base-uri
                       :request-method :post
                       :body (json/write-str nb))
              (ltu/body->edn)
              (ltu/is-status 201)))))

    ;;Any non 201 error from VPN API should be reported
    (with-redefs [utils/call-vpn-api (fn [mock-vpn-ip _ _ _] [400 "an error"])]
      (doseq [entry #{valid-nuvlabox valid-nano}]
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str entry))

            (ltu/body->edn)
            (ltu/is-status 400))))


    (with-redefs [utils/call-vpn-api (fn [_ _ _ _] [201 sample-response-map])]
      ;; creating a nuvlabox as a normal user should succeed
      (doseq [entry #{(assoc valid-nuvlabox :macAddress "02:02:02:02:02:02") valid-nano}]
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str entry))
            (ltu/body->edn)
            (ltu/is-status 201))))

    ;;when the box has already VPN infos
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-nuvlabox
                                         :sslKey "kkk"
                                         :sslCA "ca"
                                         :sslCert "cert"
                                         :macAddress "00:00:00:00:00:00")))
        (ltu/body->edn)
        (ltu/is-status 201))

    ;; Need some time for complete removal of the nuvlabox-records
    (Thread/sleep 2000)

    ;;incomplete record should have been deleted when vpn API did not succeed
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 9))



    ;; create & actions
    (with-redefs [utils/call-vpn-api (fn [_ _ _ _] [201 sample-response-map])]
      (let [resp-admin (-> session-admin
                           (request base-uri
                                    :request-method :post
                                    :body (json/write-str (assoc valid-nuvlabox :macAddress "01:bb:cc:dd:ee:ff")))
                           (ltu/body->edn)
                           (ltu/is-status 201))


            resp-nano (-> session-admin
                          (request base-uri
                                   :request-method :post
                                   :body (json/write-str (assoc valid-nano :macAddress "02:bb:cc:dd:ee:ff")))
                          (ltu/body->edn)
                          (ltu/is-status 201))

            id-nuvlabox (get-in resp-admin [:response :body :resource-id])
            id-nano (get-in resp-nano [:response :body :resource-id])
            location-admin (str p/service-context (-> resp-admin ltu/location))
            location-nano (str p/service-context (-> resp-nano ltu/location))
            uri-nuvlabox (str p/service-context id-nuvlabox)
            uri-nano (str p/service-context id-nano)
            new-nuvlabox (-> session-admin
                             (request uri-nuvlabox)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-key-value :state nb/state-new)
                             (ltu/is-key-value :refreshInterval nb/default-refresh-interval)
                             (ltu/is-key-value :id "nuvlabox-record/01bbccddeeff")
                             (ltu/is-operation-present "delete")
                             (ltu/is-operation-present "edit")
                             (ltu/is-operation-absent "quarantine")
                             (ltu/is-operation-present "activate"))
            _ (-> session-admin
                  (request uri-nano)
                  (ltu/body->edn)
                  (ltu/is-status 200)

                  (ltu/is-key-value :state nb/state-new)
                  (ltu/is-key-value :refreshInterval nb/default-refresh-interval)
                  (ltu/is-key-value :id "nuvlabox-record/02bbccddeeff")
                  (ltu/is-operation-present "delete")
                  (ltu/is-operation-present "edit")
                  (ltu/is-operation-absent "quarantine")
                  (ltu/is-operation-present "activate"))

            new-nano (-> session-admin
                         (request uri-nano)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/is-key-value :state nb/state-new)
                         (ltu/is-key-value :refreshInterval nb/default-refresh-interval)
                         (ltu/is-key-value :id "nuvlabox-record/02bbccddeeff")
                         (ltu/is-key-value :connector nil)
                         (ltu/is-operation-present "delete")
                         (ltu/is-operation-present "edit")
                         (ltu/is-operation-absent "quarantine")
                         (ltu/is-operation-present "activate"))
            activate-url-action (str p/service-context (ltu/get-op new-nuvlabox "activate"))
            activate-nano-action (str p/service-context (ltu/get-op new-nano "activate"))]

        (is (= (str nb/resource-type "/01bbccddeeff") id-nuvlabox))

        (is (= location-admin uri-nuvlabox))

        ;; adding the same resource twice should give a conflict
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str valid-nuvlabox))
            (ltu/body->edn)
            (ltu/is-status 201))
        (-> session-admin
            (request base-uri
                     :request-method :post
                     :body (json/write-str valid-nuvlabox))
            (ltu/body->edn)
            (ltu/is-status 409))

        ;; user should be able to see the resource
        (-> session-jane
            (request uri-nuvlabox)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; anonymous should not be able to see the resource
        (-> session-anon
            (request uri-nuvlabox)
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; admin should be able to edit the resource
        (-> session-admin
            (request uri-nuvlabox
                     :request-method :put
                     :body (json/write-str (assoc valid-nuvlabox :comment "just a comment")))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;;Adding vpn infos
        (let [entry (assoc valid-nano :macAddress "mm:nn:oo:pp:qq:rr")
              resp-nano (-> session-admin
                            (request base-uri
                                     :request-method :post


                                     :body (json/write-str entry))

                            (ltu/body->edn)
                            (ltu/is-status 201))
              id-nano (get-in resp-nano [:response :body :resource-id])
              uri-nano-testvpn (str p/service-context id-nano)]

          (-> session-admin
              (request uri-nano-testvpn)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state nb/state-new)
              (ltu/is-key-value :vpnIP mock-vpn-ip))


          ;; create namespace (required by service offer creation which is a side effect of activation)
          (def valid-namespace {:prefix "schema-org"
                                :uri    "https://schema-org/a/b/c.md"})
          (-> session-admin
              (request (str p/service-context sn/resource-type)
                       :request-method :post
                       :body (json/write-str valid-namespace))
              (ltu/body->edn)
              (ltu/is-status 201))


          ;; anonymous should only be able to call activate op
          (let [username (-> session-anon
                             (request activate-url-action :request-method :post)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/has-key :password)
                             (get-in [:response :body :username]))

                username-nano (-> session-anon
                                  (request activate-nano-action :request-method :post)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/has-key :password)
                                  (get-in [:response :body :username]))
                ;; user of nuvlabox should be able to view the nuvlabox resource
                session-nuvlabox-user (header session authn-info-header (str username " USER ANON"))
                activated-nuvlabox (-> session-nuvlabox-user
                                       (request uri-nuvlabox)
                                       (ltu/body->edn)
                                       (ltu/is-status 200)
                                       (ltu/is-key-value :state nb/state-activated)
                                       (ltu/is-operation-present "delete")
                                       (ltu/is-operation-present "edit")
                                       (ltu/is-operation-present "quarantine")
                                       (ltu/is-operation-absent "activate")
                                       (ltu/has-key :connector)
                                       (ltu/is-key-value :href :user username)
                                       (ltu/is-key-value :href :info "nuvlabox-state/01bbccddeeff"))



                activated-nano (-> session-admin
                                   (request uri-nano)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/is-key-value :state nb/state-activated)
                                   (ltu/is-operation-present "delete")
                                   (ltu/is-operation-present "edit")
                                   (ltu/is-operation-present "quarantine")
                                   (ltu/is-operation-absent "activate")
                                   (ltu/is-key-value :href :user username-nano)
                                   (ltu/is-key-value :href :info "nuvlabox-state/02bbccddeeff")
                                   (get-in [:response :body]))

                quarantine-url-action (str p/service-context (ltu/get-op activated-nuvlabox "quarantine"))]


            ;; call activate on already activated nuvlaboxes should fail

            (doseq [activate-operation #{activate-url-action activate-nano-action}]
              (-> session-anon
                  (request activate-operation
                           :request-method :post)
                  (ltu/body->edn)
                  (ltu/is-status 400)))

            ;; nuvlabox user is able to update the activated resource
            (-> session-nuvlabox-user
                (request uri-nuvlabox
                         :request-method :put
                         :body (json/write-str valid-nuvlabox))
                (ltu/body->edn)
                (ltu/is-status 200))

            ;; anonymous user should not be able to call quarantine
            (-> session-anon

                (request quarantine-url-action
                         :request-method :post)
                (ltu/body->edn)
                (ltu/is-status 403))

            ;; user should not be able to call quarantine
            (-> session-jane
                (request quarantine-url-action
                         :request-method :post)
                (ltu/body->edn)
                (ltu/is-status 403))



            (-> session-admin
                (request uri-nuvlabox
                         :request-method :put
                         :body (json/write-str valid-nuvlabox))
                (ltu/body->edn)
                (ltu/is-status 200))



            (-> session-jane
                (request uri-nuvlabox
                         :request-method :put
                         :body (json/write-str valid-nuvlabox))
                (ltu/body->edn)
                (ltu/is-status 403))



            (-> session-anon
                (request uri-nuvlabox
                         :request-method :put
                         :body (json/write-str valid-nuvlabox))
                (ltu/body->edn)
                (ltu/is-status 403))

            ;;Testing deletions

            ;;initial count
            (-> session-admin
                (request base-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 13))

            ;;without VPN deletion should fail

            (with-redefs [utils/remove-vpn-configuration! (fn [nuvlabox] {:status 503})]
              (-> session-admin
                  (request uri-nano
                           :request-method :delete)
                  (ltu/body->edn)
                  (ltu/is-status 503)))


            (with-redefs [utils/remove-vpn-configuration! (fn [nuvlabox] {:status 204 :message "OK"})]
              (-> session-admin
                  (request uri-nano
                           :request-method :delete)
                  (ltu/body->edn)
                  (ltu/is-status 200)))

            ;; Need some time for complete removal of the nuvlabox-record
            (Thread/sleep 2000)

            ;;after deletion count
            (-> session-admin
                (request base-uri)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-count 12))

            (with-redefs [utils/remove-vpn-configuration! (fn [nuvlabox] {:status 204 :message "OK"})]
              ;; admin can delete the resource
              (-> session-admin
                  (request uri-nuvlabox
                           :request-method :delete)
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; check that the resource is gone
              (-> session-admin
                  (request uri-nano
                           :request-method :delete)
                  (ltu/body->edn)
                  (ltu/is-status 404)))))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb/resource-type))]
    (doall
      (for [[uri method] [[base-uri :options]
                          [base-uri :delete]
                          [resource-uri :options]
                          [resource-uri :post]]]
        (-> (session (ltu/ring-app))
            (request uri
                     :request-method method
                     :body (json/write-str {:dummy "value"}))
            (ltu/is-status 405))))))
