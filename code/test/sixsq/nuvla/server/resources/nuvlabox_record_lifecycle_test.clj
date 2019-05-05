(ns sixsq.nuvla.server.resources.nuvlabox-record-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.data-record-key-prefix :as sn]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox-record :as nb]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]))

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
                     :formFactor   "Nuvlabox"
                     :macAddress   "aa:bb:cc:dd:ee:ff"
                     :owner        {:href "test"}
                     :vmCidr       "10.0.0.0/24"
                     :lanCidr      "10.0.1.0/24"})


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

    ;; Admin creation.
    (doseq [entry #{valid-nuvlabox valid-nano}]
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc entry :macAddress (random-mac-address))))
          (ltu/body->edn)
          (ltu/is-status 201)))

    ;; Need some time for complete removal of the nuvlabox-records
    (Thread/sleep 2000)

    ;;incomplete record should have been deleted when vpn API did not succeed
    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 2))

    ;; creating a nuvlabox as a normal user should succeed; both nano and regular
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nuvlabox))
        (ltu/body->edn)
        (ltu/is-status 201))

    (-> session-jane
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nano))
        (ltu/body->edn)
        (ltu/is-status 201))

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

      ;; same owners, different macAddresses
      (doseq [nb #{(assoc nb1 :macAddress (random-mac-address)) (assoc nb2 :macAddress (random-mac-address))}]
        (-> session-jane
            (request base-uri
                     :request-method :post
                     :body (json/write-str nb))
            (ltu/body->edn)
            (ltu/is-status 201))))

    ;; creating a nuvlabox as a normal user should succeed
    (doseq [entry [(assoc valid-nuvlabox :macAddress "02:02:02:02:02:02")
                   (assoc valid-nano :macAddress "12:12:12:12:12:12")]]
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str entry))
          (ltu/body->edn)
          (ltu/is-status 201)))

    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-nuvlabox :macAddress "00:00:00:00:00:00")))
        (ltu/body->edn)
        (ltu/is-status 201))

    ;; Need some time for complete removal of the nuvlabox-records
    (Thread/sleep 2000)

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 11))

    ;; create & actions
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
          new-nuvlabox-id (-> new-nuvlabox :response :body :id)
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
                       (ltu/is-operation-present "delete")
                       (ltu/is-operation-present "edit")
                       (ltu/is-operation-absent "quarantine")
                       (ltu/is-operation-present "activate"))
          new-nano-id (-> new-nano :response :body :id)
          activate-url-action (str p/service-context (ltu/get-op new-nuvlabox "activate"))
          activate-nano-action (str p/service-context (ltu/get-op new-nano "activate"))]

      (is (= (str nb/resource-type "/01bbccddeeff") id-nuvlabox))

      (is (= location-admin uri-nuvlabox))

      ;; adding the same resource twice should give a conflict
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-nuvlabox :macAddress "01:02:03:04:05:06")))
          (ltu/body->edn)
          (ltu/is-status 201))

      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-nuvlabox :macAddress "01:02:03:04:05:06")))
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
          (ltu/is-status 200)))))


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
