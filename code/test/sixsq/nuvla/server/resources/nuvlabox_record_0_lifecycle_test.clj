(ns sixsq.nuvla.server.resources.nuvlabox-record-0-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox-record :as nb]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def user "jane")


(defn random-mac-address []
  (let [random-str (apply str (remove #((set "-") %) (u/random-uuid)))]
    (->> random-str
         (partition 2)
         (take 6)
         (map #(apply str %))
         (interpose ":")
         (apply str))))


(def valid-nuvlabox {:created          timestamp
                     :updated          timestamp
                     :acl              {:owners   ["group/nuvla-admin"]
                                        :view-acl ["group/nuvla-user"]}

                     :version          0

                     :mac-address      "aa:bb:cc:dd:ee:ff"
                     :owner            {:href "test"}
                     :organization     "ACME"
                     :os-version       "OS version"
                     :hw-revision-code "a020d3"
                     :login-username   "aLoginName"
                     :login-password   "aLoginPassword"

                     :form-factor      "Nuvlabox"
                     :vm-cidr          "10.0.0.0/24"
                     :lan-cidr         "10.0.1.0/24"})


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
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-nuvlabox :mac-address (random-mac-address))))
        (ltu/body->edn)
        (ltu/is-status 201))

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 1))

    ;; creating a nuvlabox as a normal user should succeed; both nano and regular
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-nuvlabox))
        (ltu/body->edn)
        (ltu/is-status 201))

    ;; creating a nuvlabox as a normal user should succeed
    (let [entry (assoc valid-nuvlabox :mac-address "02:02:02:02:02:02")]
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str entry))
          (ltu/body->edn)
          (ltu/is-status 201)))

    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-nuvlabox :mac-address "00:00:00:00:00:00")))
        (ltu/body->edn)
        (ltu/is-status 201))

    (-> session-admin
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-count 4))

    ;; create & actions
    (let [resp-admin (-> session-admin
                         (request base-uri
                                  :request-method :post
                                  :body (json/write-str (assoc valid-nuvlabox :mac-address "01:bb:cc:dd:ee:ff")))
                         (ltu/body->edn)
                         (ltu/is-status 201))


          id-nuvlabox (get-in resp-admin [:response :body :resource-id])
          location-admin (str p/service-context (-> resp-admin ltu/location))
          uri-nuvlabox (str p/service-context id-nuvlabox)]

      (is (= (str nb/resource-type "/01bbccddeeff") id-nuvlabox))

      (is (= location-admin uri-nuvlabox))

      ;; adding the same resource twice should give a conflict
      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-nuvlabox :mac-address "01:02:03:04:05:06")))
          (ltu/body->edn)
          (ltu/is-status 201))

      (-> session-admin
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-nuvlabox :mac-address "01:02:03:04:05:06")))
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
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
