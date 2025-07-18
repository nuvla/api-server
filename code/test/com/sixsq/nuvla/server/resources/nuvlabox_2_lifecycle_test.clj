(ns com.sixsq.nuvla.server.resources.nuvlabox-2-lifecycle-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [are deftest is testing use-fixtures]]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.resource-creation :as resource-creation]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration :as configuration]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.configuration-template :as configuration-tpl]
    [com.sixsq.nuvla.server.resources.configuration-template-vpn-api :as configuration-tpl-vpn]
    [com.sixsq.nuvla.server.resources.credential :as cred]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential-template :as cred-tpl]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-registry :as cred-tpl-registry]
    [com.sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [com.sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [com.sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template-generic :as infra-service-tpl-generic]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template-vpn :as infra-srvc-tpl-vpn]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.nuvlabox-2 :as nb-2]
    [com.sixsq.nuvla.server.resources.nuvlabox-playbook :as nb-playbook]
    [com.sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [com.sixsq.nuvla.server.util.response :as r]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context nb/resource-type))

(def playbook-base-uri (str p/service-context nb-playbook/resource-type))

(def isg-collection-uri (str p/service-context isg/resource-type))

(def infra-service-collection-uri (str p/service-context infra-service/resource-type))

(def credential-collection-uri (str p/service-context credential/resource-type))

(def timestamp "1964-08-25T10:00:00Z")

(def nuvlabox-owner "user/alpha")

(def session-id "session/324c6138-aaaa-bbbb-cccc-af3ad15815db")


(def nb-name "nb-test")

(def valid-nuvlabox {:created          timestamp
                     :updated          timestamp

                     ;; This doesn't need to be specified as it will default to the
                     ;; latest version (which is currently 2). If new versions are added,
                     ;; the following line must be uncommented; the value must be the
                     ;; version number to test.
                     :version          2

                     :name             nb-name
                     :organization     "ACME"
                     :hw-revision-code "a020d3"
                     :login-username   "aLoginName"
                     :login-password   "aLoginPassword"

                     :form-factor      "Nuvlabox"
                     :lan-cidr         "10.0.1.0/24"
                     :ssh-keys         ["credential/aaa-bbb-ccc"]
                     :capabilities     ["RANDOM" utils/capability-job-pull]})


(def admin-group-name "Nuvla Administrator Group")


(deftest check-metadata
  (mdtu/check-metadata-exists nb/resource-type
                              (str nb/resource-type "-" nb-2/schema-version)))


(deftest create-delete-lifecycle
  ;; Disable stripe
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          authn-info    {:user-id      "user/alpha"
                         :active-claim "user/alpha"
                         :claims       ["group/nuvla-anon" "user/alpha" "group/nuvla-user"]}
          nuvlabox-id   (-> session-owner
                            (request base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
          nuvlabox-url  (str p/service-context nuvlabox-id)
          _             (ltu/is-last-event nuvlabox-id
                                           {:name               "nuvlabox.add"
                                            :description        (str "user/alpha added nuvlabox " nb-name)
                                            :category           "add"
                                            :success            true
                                            :linked-identifiers []
                                            :authn-info         authn-info
                                            :acl                {:owners ["group/nuvla-admin" "user/alpha"]}})

          {:keys [id acl owner]} (-> session-owner
                                     (content-type "application/x-www-form-urlencoded")
                                     (request nuvlabox-url
                                              :body (rc/form-encode {:select "id, acl, owner, operations, state"}))
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :edit)
                                     (ltu/is-operation-present :delete)
                                     (ltu/is-operation-present :activate)
                                     (ltu/is-operation-absent :commission)
                                     (ltu/is-operation-absent :decommission)
                                     (ltu/is-key-value :state "NEW")
                                     (ltu/body))]

      ;; check generated ACL
      (is (contains? (set (:owners acl)) "group/nuvla-admin"))
      (is (contains? (set (:manage acl)) id))
      (is (contains? (set (:edit-acl acl)) owner))

      (testing "query with minimal select should not fail"
        (is (= (-> session-owner
                   (content-type "application/x-www-form-urlencoded")
                   (request base-uri
                            :request-method :put
                            :body (rc/form-encode {:select "state, operations"}))
                   (ltu/body->edn)
                   (ltu/is-status 200)
                   ltu/body
                   :resources
                   first)
               {:id         id
                :operations [{:href id
                              :rel  "edit"}
                             {:href id
                              :rel  "delete"}
                             {:href (str id "/activate")
                              :rel  "activate"}
                             {:href (str id "/enable-host-level-management")
                              :rel  "enable-host-level-management"}
                             {:href (str id "/create-log")
                              :rel  "create-log"}
                             {:href (str id "/generate-new-api-key")
                              :rel  "generate-new-api-key"}]
                :state      "NEW"})))

      (-> session-owner
          (request nuvlabox-url
                   :request-method :delete)
          (ltu/is-status 200))

      (ltu/is-last-event nuvlabox-id
                         {:name               "nuvlabox.delete"
                          :description        (str "user/alpha deleted nuvlabox " nb-name)
                          :category           "delete"
                          :success            true
                          :linked-identifiers []
                          :authn-info         authn-info
                          :acl                {:owners ["group/nuvla-admin" "user/alpha"]}}))))


(deftest create-activate-create-log-decommission-delete-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session          (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-admin    (header session authn-info-header (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
          session-owner    (header session authn-info-header (str "user/alpha user/alpha group/nuvla-user group/nuvla-anon " session-id))
          session-anon     (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")
          authn-info-admin {:user-id      "group/nuvla-admin"
                            :active-claim "group/nuvla-admin"
                            :claims       ["group/nuvla-admin" "group/nuvla-anon" "group/nuvla-user" session-id]}
          authn-info-owner {:user-id      "user/alpha"
                            :active-claim "user/alpha"
                            :claims       ["group/nuvla-anon" "user/alpha" "group/nuvla-user" session-id]}
          authn-info-anon  {:user-id      "user/unknown"
                            :active-claim "user/unknown"
                            :claims       #{"user/unknown" "group/nuvla-anon"}}]

      (doseq [[session authn-info user-name-or-id]
              [[session-admin authn-info-admin admin-group-name]
               [session-owner authn-info-owner "user/alpha"]]]
        (let [nuvlabox-id  (-> session
                               (request base-uri
                                        :request-method :post
                                        :body (j/write-value-as-string (assoc valid-nuvlabox
                                                                         :owner nuvlabox-owner)))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location))
              nuvlabox-url (str p/service-context nuvlabox-id)
              _            (ltu/is-last-event nuvlabox-id
                                              {:name               "nuvlabox.add"
                                               :description        (str user-name-or-id " added nuvlabox " nb-name)
                                               :category           "add"
                                               :success            true
                                               :linked-identifiers []
                                               :authn-info         authn-info
                                               :acl                {:owners ["group/nuvla-admin" "user/alpha"]}})


              activate-url (-> session
                               (request nuvlabox-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-operation-present :edit)
                               (ltu/is-operation-present :delete)
                               (ltu/is-operation-present :activate)
                               ; check-api has been disabled in version 2
                               (ltu/is-operation-absent :check-api)
                               (ltu/is-operation-absent :commission)
                               (ltu/is-operation-absent :decommission)
                               (ltu/is-key-value :state "NEW")
                               (ltu/get-op-url :activate))]

          ;; anonymous should be able to activate the NuvlaBox
          ;; and receive an api key/secret pair to access Nuvla
          (let [credential-url      (-> session-anon
                                        (request activate-url
                                                 :request-method :post)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-key-value (comp not str/blank?) :secret-key true)
                                        (ltu/body)
                                        :api-key
                                        (ltu/href->url))
                _                   (ltu/is-last-event nuvlabox-id
                                                       {:name               "nuvlabox.activate"
                                                        :description        "user/unknown activated nuvlabox"
                                                        :category           "action"
                                                        :success            true
                                                        :linked-identifiers []
                                                        :authn-info         authn-info-anon
                                                        :acl                {:owners ["group/nuvla-admin" "user/alpha"]}})

                credential-nuvlabox (-> session-admin
                                        (request credential-url)
                                        (ltu/body->edn)
                                        (ltu/is-status 200)
                                        (ltu/is-key-value :parent nuvlabox-id)
                                        (ltu/body))

                claims              (:claims credential-nuvlabox)

                {:keys [infrastructure-service-group
                        nuvlabox-status]} (-> session-admin
                                              (request nuvlabox-url)
                                              (ltu/body->edn)
                                              (ltu/is-status 200)
                                              (ltu/body))]

            ;; check ACL and claims of generated credential.
            (is (= (:identity claims) nuvlabox-id))
            (is (= (-> claims :roles set) #{nuvlabox-id
                                            "group/nuvla-user"
                                            "group/nuvla-anon"
                                            "group/nuvla-nuvlabox"}))

            ;; checks created api credential for NB visible for owner
            (-> session-owner
                (request credential-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-absent :edit)
                (ltu/is-operation-absent :delete))

            ;; verify that an infrastructure-service-group has been created for this nuvlabox
            (-> session-owner
                (request (str p/service-context infrastructure-service-group))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-absent :edit)
                (ltu/is-operation-absent :delete))

            ;; verify that an nuvlabox-status has been created for this nuvlabox
            (-> session-owner
                (request (str p/service-context nuvlabox-status))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-absent :edit)
                (ltu/is-operation-absent :delete)))

          (let [response         (-> session
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :edit)
                                     (ltu/is-operation-absent :delete)
                                     (ltu/is-operation-absent :activate)
                                     (ltu/is-operation-present :commission)
                                     (ltu/is-operation-present :create-log)
                                     (ltu/is-operation-present :decommission)
                                     (ltu/is-key-value :state "ACTIVATED"))
                decommission-url (ltu/get-op-url response "decommission")
                create-log-url   (ltu/get-op-url response "create-log")]

            ;; check create-log operation
            (let [log-url (-> session
                              (request create-log-url
                                       :request-method :post
                                       :body (j/write-value-as-string {:components ["agent" "security"]}))
                              (ltu/body->edn)
                              (ltu/is-status 201)
                              (ltu/location-url))]

              (testing "verify that the log resource exists and acl is owned by
              nuvla-admin group id and view-acl is set for the session id"
                (-> session
                    (request log-url)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :components ["agent" "security"])
                    (ltu/is-key-value :owners :acl ["group/nuvla-admin"])
                    (ltu/is-key-value :delete :acl [session-id])
                    (ltu/is-key-value :view-acl :acl [session-id]))))

            (let [job-id (-> session
                             (request decommission-url
                                      :request-method :post)
                             (ltu/body->edn)
                             (ltu/is-status 202)
                             (ltu/location))]

              (ltu/is-last-event nuvlabox-id
                                 {:name               "nuvlabox.decommission"
                                  :description        (str user-name-or-id " decommissioned nuvlabox")
                                  :category           "action"
                                  :success            true
                                  :linked-identifiers [job-id]
                                  :authn-info         authn-info
                                  :acl                {:owners ["group/nuvla-admin"]}}))

            ;; verify state of the resource and that ACL has been updated
            (let [{:keys [owner acl]} (-> session
                                          (request nuvlabox-url)
                                          (ltu/body->edn)
                                          (ltu/is-status 200)
                                          (ltu/is-operation-absent :delete)
                                          (ltu/is-operation-absent :activate)
                                          (ltu/is-operation-absent :commission)
                                          (ltu/is-operation-present :decommission)
                                          (ltu/is-key-value :state "DECOMMISSIONING")
                                          (ltu/body))]

              (is (= ["group/nuvla-admin"] (:owners acl)))
              (is (contains? (set (:manage acl)) owner))
              (is (contains? (set (:view-acl acl)) owner))
              (is (empty? (set (:edit-meta acl))))
              (is (empty? (set (:edit-data acl))))
              (is (empty? (set (:edit-acl acl))))))

          ;; normally the job would set the state to DECOMMISSIONED
          ;; set it here manually to ensure that operations are correct
          (-> session-admin
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string (assoc valid-nuvlabox :state "DECOMMISSIONED")))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; DECOMMISSIONED state with correct actions
          (-> session-owner
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-present :delete)
              (ltu/is-operation-absent :edit)
              (ltu/is-operation-absent :activate)
              (ltu/is-operation-absent :commission)
              (ltu/is-operation-absent :create-log)
              (ltu/is-operation-absent :decommission)
              (ltu/is-key-value :state "DECOMMISSIONED")
              (ltu/is-status 200))

          ;; actually delete the nuvlabox
          (-> session
              (request nuvlabox-url
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200))

          (ltu/is-last-event nuvlabox-id
                             {:name               "nuvlabox.delete"
                              :description        (str user-name-or-id " deleted nuvlabox " nb-name)
                              :category           "delete"
                              :success            true
                              :linked-identifiers []
                              :authn-info         authn-info
                              :acl                {:owners ["group/nuvla-admin"]}})

          ;; verify that the nuvlabox has been removed
          (-> session
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 404)))))))


(deftest create-set-and-delete-location-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")]

      (doseq [session [session-admin session-owner]]
        (let [nuvlabox-id  (-> session
                               (request base-uri
                                        :request-method :post
                                        :body (j/write-value-as-string (assoc valid-nuvlabox
                                                                         :owner nuvlabox-owner)))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location))
              nuvlabox-url (str p/service-context nuvlabox-id)
              location     [46.2044 6.1432 373.]
              supplier     "some-supplier"
              nuvlabox     (-> session
                               (request nuvlabox-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/body))]

          ;; admin will be able to set any value
          ;; owner will be restricted to some attributes including location
          (-> session
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string (assoc nuvlabox :location location :supplier supplier)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :location location)
              (ltu/is-key-value :supplier (if (= session session-owner) nil supplier)))

          ;; admin and owner are able to delete location attribute
          (-> session
              (request (str nuvlabox-url "?select=location")
                       :request-method :put
                       :body (j/write-value-as-string (dissoc nuvlabox :location)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :location nil)))))))


(deftest create-activate-commission-decommission-error-delete-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session          (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
          session-admin    (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner    (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon     (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")
          authn-info-admin {:user-id      "group/nuvla-admin"
                            :active-claim "group/nuvla-admin"
                            :claims       ["group/nuvla-admin" "group/nuvla-anon" "group/nuvla-user"]}
          authn-info-owner {:user-id      "user/alpha"
                            :active-claim "user/alpha"
                            :claims       ["group/nuvla-anon" "user/alpha" "group/nuvla-user"]}
          authn-info-anon  {:user-id      "user/unknown"
                            :active-claim "user/unknown"
                            :claims       #{"user/unknown" "group/nuvla-anon"}}
          tags             #{"tag-1", "tag-2"}]

      (doseq [[session authn-info user-name-or-id]
              [[session-admin authn-info-admin admin-group-name]
               [session-owner authn-info-owner "user/alpha"]]]
        (let [nuvlabox-id  (-> session
                               (request base-uri
                                        :request-method :post
                                        :body (j/write-value-as-string (assoc valid-nuvlabox
                                                                         :owner nuvlabox-owner)))
                               (ltu/body->edn)
                               (ltu/is-status 201)
                               (ltu/location))
              nuvlabox-url (str p/service-context nuvlabox-id)

              activate-url (-> session
                               (request nuvlabox-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-operation-present :edit)
                               (ltu/is-operation-present :delete)
                               (ltu/is-operation-present :activate)
                               (ltu/is-operation-absent :commission)
                               (ltu/is-operation-absent :decommission)
                               (ltu/is-key-value :state "NEW")
                               (ltu/get-op-url :activate))]

          ;; activate nuvlabox
          (-> session-anon
              (request activate-url
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value (comp not str/blank?) :secret-key true)
              (ltu/body)
              :api-key
              (ltu/href->url))

          (ltu/is-last-event nuvlabox-id
                             {:name               "nuvlabox.activate"
                              :description        "user/unknown activated nuvlabox"
                              :category           "action"
                              :success            true
                              :linked-identifiers []
                              :authn-info         authn-info-anon
                              :acl                {:owners ["group/nuvla-admin" "user/alpha"]}})

          (let [{isg-id :id} (-> session-admin
                                 (content-type "application/x-www-form-urlencoded")
                                 (request isg-collection-uri
                                          :request-method :put
                                          :body (rc/form-encode {:filter (format "parent='%s'"
                                                                                 nuvlabox-id)}))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/is-count 1)
                                 (ltu/entries)
                                 first)

                commission (-> session
                               (request nuvlabox-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-operation-present :edit)
                               (ltu/is-operation-absent :delete)
                               (ltu/is-operation-absent :activate)
                               (ltu/is-operation-present :commission)
                               (ltu/is-operation-present :decommission)
                               (ltu/is-key-value :state "ACTIVATED")
                               (ltu/get-op-url :commission))]

            ;; partial commissioning of the nuvlabox (no swarm credentials)
            (-> session
                (request commission
                         :request-method :post
                         :body (j/write-value-as-string {:cluster-worker-id   "xyz"
                                                         :swarm-token-worker  "abc"
                                                         :swarm-token-manager "def"
                                                         ;:swarm-client-key    "key"
                                                         ;:swarm-client-cert   "cert"
                                                         ;:swarm-client-ca     "ca"
                                                         :swarm-endpoint      "https://swarm.example.com"
                                                         :tags                tags
                                                         :minio-access-key    "access"
                                                         :minio-secret-key    "secret"
                                                         :minio-endpoint      "https://minio.example.com"
                                                         :capabilities        [utils/capability-job-pull]}))
                (ltu/body->edn)
                (ltu/is-status 200))

            (ltu/is-last-event nuvlabox-id
                               {:name               "nuvlabox.commission"
                                :description        (str user-name-or-id " commissioned nuvlabox")
                                :category           "action"
                                :success            true
                                :linked-identifiers []
                                :authn-info         authn-info
                                :acl                {:owners ["group/nuvla-admin" "user/alpha"]}})

            ;; verify state of the resource
            (-> session
                (request nuvlabox-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-present :edit)
                (ltu/is-operation-absent :delete)
                (ltu/is-operation-absent :activate)
                (ltu/is-operation-present :commission)
                (ltu/is-operation-present :decommission)
                (ltu/is-operation-present :cluster-nuvlabox)
                (ltu/is-key-value :state "COMMISSIONED")
                (ltu/is-key-value :tags nil)
                (ltu/is-key-value (partial mapv #(dissoc % :id))
                                  :coe-list [{:coe-type "swarm"}]))

            ;; check that services exist
            (let [services (-> session
                               (content-type "application/x-www-form-urlencoded")
                               (request infra-service-collection-uri
                                        :request-method :put
                                        :body (rc/form-encode {:filter (format
                                                                         "parent='%s'" isg-id)}))
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-count 2)
                               (ltu/entries))]

              (is (= #{"swarm" "s3"} (set (map :subtype services))))

              (doseq [{:keys [acl]} services]
                (is (= [nuvlabox-owner] (:view-acl acl))))

              (doseq [{:keys [subtype] :as service} services]
                (let [creds (-> session-owner
                                (content-type "application/x-www-form-urlencoded")
                                (request credential-collection-uri
                                         :request-method :put
                                         :body (rc/form-encode {:filter (format "parent='%s'"
                                                                                (:id service))}))
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/entries))]

                  (when (= "swarm" subtype)
                    (is (= 2 (count creds))))               ;; only swarm token credentials

                  (when (= "s3" subtype)
                    (is (= 1 (count creds)))))))            ;; only key/secret pair

            (-> session
                (request commission
                         :request-method :post
                         :body (j/write-value-as-string {:cluster-worker-id "cluster-worker-id"
                                                         :swarm-endpoint    "https://swarm.example.com"
                                                         :tags              tags
                                                         :capabilities      [utils/capability-job-pull]}))
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session
                (request nuvlabox-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-present :edit)
                (ltu/is-operation-absent :delete)
                (ltu/is-operation-absent :activate)
                (ltu/is-operation-present :commission)
                (ltu/is-operation-present :decommission)
                (ltu/is-operation-present :cluster-nuvlabox)
                (ltu/is-key-value :state "COMMISSIONED")
                (ltu/is-key-value :tags nil)
                (ltu/is-key-value (partial mapv #(dissoc % :id))
                                  :coe-list [{:coe-type "swarm"}]))

            ;; check custom operations
            ;;
            (let [cluster-nuvlabox (-> session
                                       (request nuvlabox-url)
                                       (ltu/body->edn)
                                       (ltu/is-status 200)
                                       (ltu/is-operation-present :edit)
                                       (ltu/is-operation-absent :delete)
                                       (ltu/is-operation-absent :activate)
                                       (ltu/is-operation-present :commission)
                                       (ltu/is-operation-present :decommission)
                                       (ltu/is-operation-absent :check-api)
                                       (ltu/is-operation-present :reboot)
                                       (ltu/is-operation-present :add-ssh-key)
                                       (ltu/is-operation-present :revoke-ssh-key)
                                       (ltu/is-operation-present :update-nuvlabox)
                                       (ltu/is-operation-present :cluster-nuvlabox)
                                       (ltu/is-operation-present :assemble-playbooks)
                                       (ltu/is-key-value :state "COMMISSIONED")
                                       (ltu/get-op-url :cluster-nuvlabox))]


              ;; cluster-nuvlabox-action
              (-> session
                  (request cluster-nuvlabox
                           :request-method :post
                           :body (j/write-value-as-string {:cluster-action "join-worker" :nuvlabox-manager-status {}}))
                  (ltu/body->edn)
                  (ltu/is-status 202)))


            ;; second commissioning of the resource (with swarm credentials)
            (-> session
                (request commission
                         :request-method :post
                         :body (j/write-value-as-string {:swarm-token-worker     "abc"
                                                         :swarm-token-manager    "def"
                                                         :swarm-client-key       "key"
                                                         :swarm-client-cert      "cert"
                                                         :swarm-client-ca        "ca"
                                                         :swarm-endpoint         "https://swarm.example.com"
                                                         :minio-access-key       "access"
                                                         :minio-secret-key       "secret"
                                                         :minio-endpoint         "https://minio.example.com"
                                                         :kubernetes-client-key  "key"
                                                         :kubernetes-client-cert "cert"
                                                         :kubernetes-client-ca   "ca"
                                                         :kubernetes-endpoint    "https://k8s.example.com"}))
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session
                (request nuvlabox-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-operation-present :edit)
                (ltu/is-operation-absent :delete)
                (ltu/is-operation-absent :activate)
                (ltu/is-operation-present :commission)
                (ltu/is-operation-present :decommission)
                (ltu/is-operation-present :cluster-nuvlabox)
                (ltu/is-key-value :state "COMMISSIONED")
                (ltu/is-key-value :tags nil)
                (ltu/is-key-value (partial mapv #(dissoc % :id))
                                  :coe-list [{:coe-type "docker"}
                                             {:coe-type "kubernetes"}]))

            ;; check the services again
            (let [services (-> session-owner
                               (content-type "application/x-www-form-urlencoded")
                               (request infra-service-collection-uri
                                        :request-method :put
                                        :body (rc/form-encode {:filter (format "parent='%s'"
                                                                               isg-id)}))
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-count 3)
                               (ltu/entries))]

              (is (= #{"swarm" "s3" "kubernetes"} (set (map :subtype services)))))

            ;; third commissioning of the resource make sure no additional credentials created
            (-> session
                (request commission
                         :request-method :post
                         :body (j/write-value-as-string {:swarm-token-worker  "abc"
                                                         :swarm-token-manager "def"
                                                         :swarm-client-key    "key-bad"
                                                         :swarm-client-cert   "cert-bad"
                                                         :swarm-client-ca     "ca-bad"
                                                         :swarm-endpoint      "https://swarm.example.com"
                                                         :minio-access-key    "access"
                                                         :minio-secret-key    "secret"
                                                         :minio-endpoint      "https://minio.example.com"
                                                         :capabilities        []}))
                (ltu/body->edn)
                (ltu/is-status 200))

            ;; check the services again
            (let [services (-> session-owner
                               (content-type "application/x-www-form-urlencoded")
                               (request infra-service-collection-uri
                                        :request-method :put
                                        :body (rc/form-encode {:filter (format "parent='%s'" isg-id)}))
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-count 3)
                               (ltu/entries))]

              (is (= #{"swarm" "s3" "kubernetes"} (set (map :subtype services))))

              (doseq [{:keys [subtype] :as service} services]
                (let [creds (-> session-owner
                                (content-type "application/x-www-form-urlencoded")
                                (request credential-collection-uri
                                         :request-method :put
                                         :body (rc/form-encode {:filter (format "parent='%s'"
                                                                                (:id service))}))
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/entries))]

                  (when (= "swarm" subtype)
                    (is (= 3 (count creds))))               ;; now both tokens and credential

                  (when (= "s3" subtype)
                    (is (= 1 (count creds))))               ;; only key/secret pair

                  (when (= "kubernetes" subtype)
                    (is (= 1 (count creds))))))))


          (let [decommission-resp (-> session
                                      (request nuvlabox-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-operation-present :edit)
                                      (ltu/is-operation-absent :delete)
                                      (ltu/is-operation-absent :activate)
                                      (ltu/is-operation-present :commission)
                                      (ltu/is-operation-present :decommission)
                                      (ltu/is-key-value :state "COMMISSIONED"))

                pre-acl           (-> decommission-resp
                                      (ltu/body)
                                      :acl)

                decommission-url  (ltu/get-op-url decommission-resp :decommission)]

            ;; only trigger the decommissioning; don't check details again
            (-> session
                (request decommission-url
                         :request-method :post)
                (ltu/body->edn)
                (ltu/is-status 202))

            ;; verify state of the resource
            (let [post-acl (-> session
                               (request nuvlabox-url)
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-operation-absent :delete)
                               (ltu/is-operation-absent :activate)
                               (ltu/is-operation-absent :commission)
                               (ltu/is-operation-present :decommission)
                               (ltu/is-key-value :state "DECOMMISSIONING")
                               (ltu/is-key-value :coe-list nil)
                               (ltu/body)
                               :acl)]

              ;; verify that the ACL has been changed
              (is (not= pre-acl post-acl))))

          ;; set the state to DECOMMISSIONED manually to ensure that
          ;; operations are correct
          ;; Only admin can edit a nuvlabox in DECOMMISSIONING state
          (-> session-admin
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string {:state "DECOMMISSIONED"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; verify state of the resource and operations
          (-> session
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-present :delete)
              (ltu/is-operation-absent :activate)
              (ltu/is-operation-absent :commission)
              (ltu/is-operation-absent :decommission)
              (ltu/is-key-value :state "DECOMMISSIONED"))

          ;; set state to ERROR manually to ensure that operations are correct
          ;; Only admin can edit a nuvlabox in DECOMMISSIONING state
          (-> session-admin
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string {:state "ERROR"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; verify state of the resource and operations
          (-> session
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-present :delete)
              (ltu/is-operation-absent :activate)
              (ltu/is-operation-absent :commission)
              (ltu/is-operation-present :decommission)
              (ltu/is-key-value :state "ERROR"))

          ;; detailed checks done previously, just delete the resource
          (-> session
              (request nuvlabox-url
                       :request-method :delete)
              (ltu/body->edn)
              (ltu/is-status 200)))))))


(deftest create-activate-commission-share-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")
          user-beta     "user/beta"
          session-beta  (header session authn-info-header (str user-beta " " user-beta " group/nuvla-user group/nuvla-anon"))]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (j/write-value-as-string valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))
            nuvlabox-url (str p/service-context nuvlabox-id)

            activate-url (-> session-owner
                             (request nuvlabox-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-present :edit)
                             (ltu/is-operation-present :delete)
                             (ltu/is-operation-present :activate)
                             (ltu/is-operation-absent :commission)
                             (ltu/is-operation-absent :decommission)
                             (ltu/is-key-value :state "NEW")
                             (ltu/get-op-url :activate))]

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value (comp not str/blank?) :secret-key true)
            (ltu/body)
            :api-key
            (ltu/href->url))

        (let [{isg-id :id} (-> session-owner
                               (content-type "application/x-www-form-urlencoded")
                               (request isg-collection-uri
                                        :request-method :put
                                        :body (rc/form-encode {:filter (format "parent='%s'"
                                                                               nuvlabox-id)}))
                               (ltu/body->edn)
                               (ltu/is-status 200)
                               (ltu/is-count 1)
                               (ltu/entries)
                               first)

              commission (-> session-owner
                             (request nuvlabox-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-present :edit)
                             (ltu/is-operation-absent :delete)
                             (ltu/is-operation-absent :activate)
                             (ltu/is-operation-present :commission)
                             (ltu/is-operation-present :decommission)
                             (ltu/is-key-value :state "ACTIVATED")
                             (ltu/get-op-url :commission))]

          ;; commissioning of the nuvlabox (no swarm credentials)
          (-> session-owner
              (request commission
                       :request-method :post
                       :body (j/write-value-as-string {:cluster-worker-id      "xyz"
                                                       :swarm-token-worker     "abc"
                                                       :swarm-token-manager    "def"
                                                       :swarm-client-key       "key"
                                                       :swarm-client-cert      "cert"
                                                       :swarm-client-ca        "ca"
                                                       :swarm-endpoint         "https://swarm.example.com"
                                                       :minio-access-key       "access"
                                                       :minio-secret-key       "secret"
                                                       :minio-endpoint         "https://minio.example.com"
                                                       :kubernetes-client-key  "key"
                                                       :kubernetes-client-cert "cert"
                                                       :kubernetes-client-ca   "ca"
                                                       :kubernetes-endpoint    "https://k8s.example.com"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; verify state of the resource
          (-> session-owner
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-present :edit)
              (ltu/is-operation-absent :delete)
              (ltu/is-operation-absent :activate)
              (ltu/is-operation-present :commission)
              (ltu/is-operation-present :decommission)
              (ltu/is-key-value :state "COMMISSIONED"))

          ;; check that services exist
          (let [services (-> session-owner
                             (content-type "application/x-www-form-urlencoded")
                             (request infra-service-collection-uri
                                      :request-method :put
                                      :body (rc/form-encode {:filter (format
                                                                       "parent='%s'" isg-id)}))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-count 3)
                             (ltu/entries))]

            (is (= #{"swarm" "s3" "kubernetes"} (set (map :subtype services))))

            (doseq [{:keys [acl]} services]
              (is (= [nuvlabox-owner] (:view-acl acl))))

            (doseq [{:keys [subtype] :as service} services]
              (let [creds (-> session-owner
                              (content-type "application/x-www-form-urlencoded")
                              (request credential-collection-uri
                                       :request-method :put
                                       :body (rc/form-encode {:filter (format "parent='%s'"
                                                                              (:id service))}))
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/entries))]

                (when (= "swarm" subtype)
                  (is (= 3 (count creds))))                 ;; only swarm token credentials

                (when (= "s3" subtype)
                  (is (= 1 (count creds))))                 ;; only key/secret pair

                (when (= "kubernetes" subtype)
                  (is (= 1 (count creds)))))))


          (-> session-beta
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 403))

          ;; share nuvlabox with a user beta
          (-> session-owner
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string {:acl {:edit-acl [user-beta]}}))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-beta
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-present :commission)
              (ltu/is-operation-present :decommission))

          ;; user beta is not allowed to remove owner of the box from acl even if he can edit-acl
          (-> session-beta
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string {:acl {:edit-acl [user-beta]}}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :edit-acl :acl [nuvlabox-owner user-beta]))

          ;; check that services exist are visible for invited user beta
          (let [services (-> session-beta
                             (content-type "application/x-www-form-urlencoded")
                             (request infra-service-collection-uri
                                      :request-method :put
                                      :body (rc/form-encode {:filter (format
                                                                       "parent='%s'" isg-id)}))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-count 3)
                             (ltu/entries))]

            (is (= #{"swarm" "s3" "kubernetes"} (set (map :subtype services))))

            (doseq [{:keys [acl]} services]
              (is (= [nuvlabox-owner user-beta] (:view-acl acl))))

            (doseq [{:keys [subtype] :as service} services]
              (let [creds (-> session-beta
                              (content-type "application/x-www-form-urlencoded")
                              (request credential-collection-uri
                                       :request-method :put
                                       :body (rc/form-encode {:filter (format "parent='%s'"
                                                                              (:id service))}))
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/entries))]

                (when (= "swarm" subtype)
                  (is (= 3 (count creds))))                 ;; only swarm token credentials

                (when (= "s3" subtype)
                  (is (= 1 (count creds))))                 ;; only key/secret pair

                (when (= "kubernetes" subtype)
                  (is (= 1 (count creds))))))))))))


(deftest create-activate-commission-vpn-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

      (testing "create nuvlabox with inexistent vpn id will fail"
        (-> session-owner
            (request base-uri
                     :request-method :post
                     :body (j/write-value-as-string (assoc valid-nuvlabox
                                                      :vpn-server-id "infrastructure-service/fake")))
            (ltu/body->edn)
            (ltu/is-status 404)))

      (let [infra-srvc-vpn-create {:template {:href      (str infra-service-tpl/resource-type "/"
                                                              infra-srvc-tpl-vpn/method)
                                              :vpn-scope "nuvlabox"
                                              :acl       {:owners   ["nuvla/admin"]
                                                          :view-acl ["nuvla/user"
                                                                     "nuvla/nuvlabox"]}}}
            infra-srvc-vpn-id     (-> session-admin
                                      (request (str p/service-context infra-service/resource-type)
                                               :request-method :post
                                               :body (j/write-value-as-string infra-srvc-vpn-create))
                                      (ltu/body->edn)
                                      (ltu/is-status 201)
                                      (ltu/location))

            conf-vpn-create       {:template
                                   {:href                    (str configuration-tpl/resource-type "/"
                                                                  configuration-tpl-vpn/service)
                                    :instance                "vpn"
                                    :endpoint                "http://vpn.test"
                                    :infrastructure-services [infra-srvc-vpn-id]}}

            nuvlabox-id           (-> session-owner
                                      (request base-uri
                                               :request-method :post
                                               :body (j/write-value-as-string
                                                       (assoc valid-nuvlabox
                                                         :vpn-server-id infra-srvc-vpn-id)))
                                      (ltu/body->edn)
                                      (ltu/is-status 201)
                                      (ltu/location))

            nuvlabox-url          (str p/service-context nuvlabox-id)

            activate-url          (-> session-owner
                                      (request nuvlabox-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-key-value :state "NEW")
                                      (ltu/is-key-value :view-acl :acl [infra-srvc-vpn-id
                                                                        "user/alpha"])
                                      (ltu/get-op-url :activate))]

        (-> session-admin
            (request (str p/service-context configuration/resource-type)
                     :request-method :post
                     :body (j/write-value-as-string conf-vpn-create))
            (ltu/body->edn)
            (ltu/is-status 201))

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [session-nuvlabox  (header session authn-info-header
                                        (str nuvlabox-id " " nuvlabox-id
                                             " group/nuvla-nuvlabox group/nuvla-anon"))
              commission        (-> session-owner
                                    (request nuvlabox-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/get-op-url :commission))
              certificate-value "certificate-value"
              common-name-value "foo"
              inter-ca-values   ["inter-ca-values"]]


          ;; commissioning of the resource
          (with-redefs [vpn-utils/generate-credential (fn [_ _ _ _]
                                                        {:certificate     certificate-value
                                                         :common-name     common-name-value
                                                         :intermediate-ca inter-ca-values})
                        vpn-utils/delete-credential   (fn [_ _])]

            (-> session-nuvlabox
                (request commission
                         :request-method :post
                         :body (j/write-value-as-string {:vpn-csr "foo"}))
                (ltu/body->edn)
                (ltu/is-status 200))

            (let [filter-str  (str "parent='" infra-srvc-vpn-id
                                   "' and vpn-certificate-owner='" nuvlabox-id "'")
                  vpn-cred    (-> session-nuvlabox
                                  (content-type "application/x-www-form-urlencoded")
                                  (request credential-collection-uri
                                           :request-method :put
                                           :body (rc/form-encode
                                                   {:filter filter-str}))
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-count 1)
                                  (ltu/entries)
                                  first)
                  vpn-cred-id (:id vpn-cred)]

              (is (= common-name-value (:vpn-common-name vpn-cred)))

              (-> session-nuvlabox
                  (request (str p/service-context vpn-cred-id)
                           :request-method :get)
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; commison a second time will delete and recreate a new cred
              (-> session-nuvlabox
                  (request commission
                           :request-method :post
                           :body (j/write-value-as-string {:vpn-csr "foo"}))
                  (ltu/body->edn)
                  (ltu/is-status 200))

              ;; old vpn credential was deleted
              (-> session-nuvlabox
                  (request (str p/service-context vpn-cred-id)
                           :request-method :get)
                  (ltu/body->edn)
                  (ltu/is-status 404))

              ;; a new vpn credential was recreated
              (-> session-nuvlabox
                  (content-type "application/x-www-form-urlencoded")
                  (request credential-collection-uri
                           :request-method :put
                           :body (rc/form-encode
                                   {:filter filter-str}))
                  (ltu/body->edn)
                  (ltu/is-status 200)
                  (ltu/is-count 1)))))))))


(deftest create-activate-commission-re-commision
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [infra-srvc-vpn-create {:template {:href      (str infra-service-tpl/resource-type "/"
                                                              infra-srvc-tpl-vpn/method)
                                              :vpn-scope "nuvlabox"
                                              :acl       {:owners   ["nuvla/admin"]
                                                          :view-acl ["nuvla/user"
                                                                     "nuvla/nuvlabox"]}}}
            infra-srvc-vpn-id     (-> session-admin
                                      (request (str p/service-context infra-service/resource-type)
                                               :request-method :post
                                               :body (j/write-value-as-string infra-srvc-vpn-create))
                                      (ltu/body->edn)
                                      (ltu/is-status 201)
                                      (ltu/location))

            conf-vpn-create       {:template
                                   {:href                    (str configuration-tpl/resource-type "/"
                                                                  configuration-tpl-vpn/service)
                                    :instance                "vpn"
                                    :endpoint                "http://vpn.test"
                                    :infrastructure-services [infra-srvc-vpn-id]}}

            nuvlabox-id           (-> session-owner
                                      (request base-uri
                                               :request-method :post
                                               :body (j/write-value-as-string
                                                       (assoc valid-nuvlabox
                                                         :vpn-server-id infra-srvc-vpn-id)))
                                      (ltu/body->edn)
                                      (ltu/is-status 201)
                                      (ltu/location))

            nuvlabox-url          (str p/service-context nuvlabox-id)

            activate-url          (-> session-owner
                                      (request nuvlabox-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-key-value :state "NEW")
                                      (ltu/is-key-value :view-acl :acl [infra-srvc-vpn-id
                                                                        "user/alpha"])
                                      (ltu/get-op-url :activate))]

        (-> session-admin
            (request (str p/service-context configuration/resource-type)
                     :request-method :post
                     :body (j/write-value-as-string conf-vpn-create))
            (ltu/body->edn)
            (ltu/is-status 201))

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [session-nuvlabox  (header session authn-info-header
                                        (str nuvlabox-id " " nuvlabox-id
                                             " group/nuvla-nuvlabox group/nuvla-anon"))
              commission        (-> session-owner
                                    (request nuvlabox-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/get-op-url :commission))
              certificate-value "certificate-value"
              common-name-value "foo"
              inter-ca-values   ["inter-ca-values"]
              infra-srvc-grp-id (-> session-owner
                                    (request nuvlabox-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    :response
                                    :body
                                    :infrastructure-service-group)]


          ;; commissioning of the resource
          (with-redefs [vpn-utils/generate-credential (fn [_ _ _ _]
                                                        {:certificate     certificate-value
                                                         :common-name     common-name-value
                                                         :intermediate-ca inter-ca-values})
                        vpn-utils/delete-credential   (fn [_ _])]

            (-> session-nuvlabox
                (request commission
                         :request-method :post
                         :body (j/write-value-as-string {:swarm-endpoint "http://foo"}))
                (ltu/body->edn)
                (ltu/is-status 200))

            (let [srvc-endpoint (-> session-owner
                                    (content-type "application/x-www-form-urlencoded")
                                    (request (str p/service-context infra-service/resource-type)
                                             :request-method :put
                                             :body (rc/form-encode {:filter (format "parent='%s'" infra-srvc-grp-id)}))
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/entries)
                                    first)]
              (is (= (:endpoint srvc-endpoint) "http://foo"))
              (is (= (:name srvc-endpoint) nb-name)))

            (-> session-nuvlabox
                (request commission
                         :request-method :post
                         :body (j/write-value-as-string {:swarm-endpoint "http://bar"}))
                (ltu/body->edn)
                (ltu/is-status 200))

            (let [srvc-endpoint (-> session-owner
                                    (content-type "application/x-www-form-urlencoded")
                                    (request (str p/service-context infra-service/resource-type)
                                             :request-method :put
                                             :body (rc/form-encode
                                                     {:filter (format "parent='%s'"
                                                                      infra-srvc-grp-id)}))
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/entries)
                                    first)]
              (is (= (:endpoint srvc-endpoint) "http://bar"))
              (is (= (:name srvc-endpoint) nb-name)))))))))


(deftest execution-mode-action-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")

          nuvlabox-id   (-> session-owner
                            (request base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
          nuvlabox-url  (str p/service-context nuvlabox-id)]

      (-> session-admin
          (request nuvlabox-url
                   :request-method :put
                   :body (j/write-value-as-string {:state "COMMISSIONED"}))
          (ltu/body->edn)
          (ltu/is-status 200))

      ;; pull
      (let [reboot-url (-> session-owner
                           (request nuvlabox-url)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/get-op-url :reboot))
            job-url    (-> session-owner
                           (request reboot-url :request-method :post)
                           (ltu/body->edn)
                           (ltu/is-status 202)
                           (ltu/location-url))]
        (-> session-admin
            (request job-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :execution-mode "pull")
            (ltu/is-key-value :acl {:edit-data [nuvlabox-id "user/alpha"],
                                    :owners    ["group/nuvla-admin"],
                                    :view-acl  ["user/alpha"],
                                    :delete    ["user/alpha"],
                                    :view-meta [nuvlabox-id "user/alpha"],
                                    :edit-acl  ["user/alpha"],
                                    :view-data [nuvlabox-id "user/alpha"],
                                    :manage    [nuvlabox-id "user/alpha"],
                                    :edit-meta [nuvlabox-id "user/alpha"]}))))))

(deftest coe-resource-action-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")

          nuvlabox-id   (-> session-owner
                            (request base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
          nuvlabox-url  (str p/service-context nuvlabox-id)]

      (-> session-admin
          (request nuvlabox-url)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-operation-absent utils/action-coe-resource-actions))

      (testing "coe resource action is not available for nuvlabox engine version 1.0.0"
        (-> session-admin
            (request nuvlabox-url
                     :request-method :put
                     :body (j/write-value-as-string {:state                   "COMMISSIONED"
                                                     :nuvlabox-engine-version "1.0.0"}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-absent utils/action-coe-resource-actions)))

      (-> session-admin
          (request nuvlabox-url
                   :request-method :put
                   :body (j/write-value-as-string {:nuvlabox-engine-version "2.17.0"}))
          (ltu/body->edn)
          (ltu/is-status 200))

      (let [coe-actions-url (-> session-owner
                                (request nuvlabox-url)
                                (ltu/body->edn)
                                (ltu/is-status 200)
                                (ltu/is-operation-present utils/action-coe-resource-actions)
                                (ltu/get-op-url utils/action-coe-resource-actions))
            job-url         (-> session-owner
                                (request coe-actions-url
                                         :request-method :post
                                         :body (j/write-value-as-string {:docker [{:action "remove" :id "xyz" :resource "image"}]}))
                                (ltu/body->edn)
                                (ltu/is-status 202)
                                (ltu/location-url))]
        (-> session-admin
            (request job-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :action "coe_resource_actions")
            (ltu/is-key-value :payload "{\"docker\":[{\"action\":\"remove\",\"id\":\"xyz\",\"resource\":\"image\"}]}")))

      (testing "coe resource action with credential"
        (let [cred-uri                 (str p/service-context credential/resource-type)
              tpl-href                 (str cred-tpl/resource-type "/" cred-tpl-registry/method)
              username                 "username"
              password                 "password"
              infra-service-group-uri  (str p/service-context isg/resource-type)
              infra-service-group-body {:name "test-infra-service-group"}
              infra-service-group-id   (-> session-owner
                                           (request infra-service-group-uri
                                                    :request-method :post
                                                    :body (j/write-value-as-string infra-service-group-body))
                                           (ltu/body->edn)
                                           (ltu/is-status 201)
                                           (ltu/body-resource-id))
              is-generic-tpl-href      (str infra-service-tpl/resource-type "/" infra-service-tpl-generic/method)
              infra-service-uri        (str p/service-context infra-service/resource-type)
              endpoint                 "https://test.registry.com"
              infra-service-body       {:name     "test-registry"
                                        :template {:href     is-generic-tpl-href
                                                   :parent   infra-service-group-id
                                                   :endpoint endpoint}}
              infra-service-id         (-> session-owner
                                           (request infra-service-uri
                                                    :request-method :post
                                                    :body (j/write-value-as-string infra-service-body))
                                           (ltu/body->edn)
                                           (ltu/is-status 201)
                                           (ltu/body-resource-id))
              cred-body                {:name        "cred-name"
                                        :description "cred-desc"
                                        :template    {:href     tpl-href
                                                      :parent   infra-service-id
                                                      :username username
                                                      :password password}}
              admin-cred-id            (-> session-admin
                                           (request cred-uri
                                                    :request-method :post
                                                    :body (j/write-value-as-string cred-body))
                                           (ltu/body->edn)
                                           (ltu/is-status 201)
                                           (ltu/body-resource-id))
              cred-id                  (-> session-owner
                                           (request cred-uri
                                                    :request-method :post
                                                    :body (j/write-value-as-string cred-body))
                                           (ltu/body->edn)
                                           (ltu/is-status 201)
                                           (ltu/body-resource-id))
              coe-actions-url          (-> session-owner
                                           (request nuvlabox-url)
                                           (ltu/body->edn)
                                           (ltu/is-status 200)
                                           (ltu/is-operation-present utils/action-coe-resource-actions)
                                           (ltu/get-op-url utils/action-coe-resource-actions))]
          (testing "User must have read rights on credentials"
            (-> session-owner
                (request coe-actions-url
                         :request-method :post
                         :body (j/write-value-as-string {:docker [{:action "remove" :id "xyz" :resource "image" :credential admin-cred-id}]}))
                (ltu/body->edn)
                (ltu/is-status 403)))
          (testing "Job created successfully for coe resource actions with credentials"
            (let [job-url          (-> session-owner
                                       (request coe-actions-url
                                                :request-method :post
                                                :body (j/write-value-as-string {:docker [{:action "remove" :id "xyz" :resource "image" :credential cred-id}]}))
                                       (ltu/body->edn)
                                       (ltu/is-status 202)
                                       (ltu/location-url))
                  job-resp         (-> session-admin
                                       (request job-url)
                                       (ltu/body->edn)
                                       (ltu/is-status 200)
                                       (ltu/is-key-value :action "coe_resource_actions")
                                       (ltu/is-key-value :payload (str "{\"docker\":[{\"action\":\"remove\",\"id\":\"xyz\",\"resource\":\"image\","
                                                                       "\"credential\":\"" cred-id "\""
                                                                       "}]}")))
                  get-context-url  (-> job-resp
                                       (ltu/is-operation-present :get-context)
                                       (ltu/get-op-url :get-context))
                  get-context-body (-> session-admin
                                       (request get-context-url)
                                       (ltu/body->edn)
                                       (ltu/is-status 200)
                                       (ltu/body))]
              (is (contains? get-context-body (keyword infra-service-id)))
              (is (contains? get-context-body (keyword cred-id)))
              (is (= endpoint (get-in get-context-body [(keyword infra-service-id) :endpoint])))
              (is (= username (get-in get-context-body [(keyword cred-id) :username])))
              (is (= password (get-in get-context-body [(keyword cred-id) :password])))))
          (testing "get-context must fail if nuvlabox owner does not have access to credentials in payload"
            (let [admin-cred-id   (-> session-admin
                                      (request cred-uri
                                               :request-method :post
                                               :body (j/write-value-as-string cred-body))
                                      (ltu/body->edn)
                                      (ltu/is-status 201)
                                      (ltu/body-resource-id))
                  job-url         (-> session-admin
                                      (request coe-actions-url
                                               :request-method :post
                                               :body (j/write-value-as-string {:docker [{:action "remove" :id "xyz" :resource "image" :credential admin-cred-id}]}))
                                      (ltu/body->edn)
                                      (ltu/is-status 202)
                                      (ltu/location-url))
                  job-resp        (-> session-admin
                                      (request job-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-key-value :action "coe_resource_actions")
                                      (ltu/is-key-value :payload (str "{\"docker\":[{\"action\":\"remove\",\"id\":\"xyz\",\"resource\":\"image\","
                                                                      "\"credential\":\"" admin-cred-id "\""
                                                                      "}]}")))
                  get-context-url (-> job-resp
                                      (ltu/is-operation-present :get-context)
                                      (ltu/get-op-url :get-context))]
              (-> session-admin
                  (request get-context-url)
                  (ltu/body->edn)
                  (ltu/is-status 403)
                  (ltu/is-key-value :message (str "invalid credentials for '" admin-cred-id "'"))))))))))


(deftest create-activate-assemble-playbooks-emergency-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (j/write-value-as-string valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

            nuvlabox-url (str p/service-context nuvlabox-id)

            activate-url (-> session-owner
                             (request nuvlabox-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/get-op-url :activate))]

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; create playbook
        (-> session-owner
            (request playbook-base-uri
                     :request-method :post
                     :body (j/write-value-as-string {:parent nuvlabox-id :type "MANAGEMENT" :run "foo MGMT" :enabled true}))
            (ltu/body->edn)
            (ltu/is-status 201))


        (let [session-nuvlabox (header session authn-info-header
                                       (str nuvlabox-id " " nuvlabox-id
                                            " group/nuvla-nuvlabox group/nuvla-anon"))]

          (let [;; create emergency playbook
                emergency-playbook-id  (-> session-owner
                                           (request playbook-base-uri
                                                    :request-method :post
                                                    :body (j/write-value-as-string {:parent nuvlabox-id :type "EMERGENCY" :run "foo EMERGENCY" :enabled false}))
                                           (ltu/body->edn)
                                           (ltu/is-status 201)
                                           (ltu/location))

                assemble-playbooks-url (-> session-owner
                                           (request nuvlabox-url)
                                           (ltu/body->edn)
                                           (ltu/is-status 200)
                                           (ltu/is-operation-present :assemble-playbooks)
                                           (ltu/get-op-url :assemble-playbooks))

                enable-emergency-url   (-> session-owner
                                           (request nuvlabox-url)
                                           (ltu/body->edn)
                                           (ltu/is-status 200)
                                           (ltu/is-operation-present :enable-emergency-playbooks)
                                           (ltu/get-op-url :enable-emergency-playbooks))

                ;; give back mgmt playbooks
                assembled-playbooks    (-> session-nuvlabox
                                           (request assemble-playbooks-url)
                                           (ltu/is-status 200)
                                           (ltu/body))

                ;; enable emergency
                _                      (-> session-owner
                                           (request enable-emergency-url
                                                    :request-method :post
                                                    :body (j/write-value-as-string {:emergency-playbooks-ids [emergency-playbook-id]}))
                                           (ltu/body->edn)
                                           (ltu/is-status 200))

                ;; the playbook is now enabled
                _                      (-> session-nuvlabox
                                           (request (str p/service-context emergency-playbook-id))
                                           (ltu/body->edn)
                                           (ltu/is-status 200)
                                           (ltu/is-key-value :enabled true))

                ;; now give back emergency playbooks
                assembled-em-playbooks (-> session-nuvlabox
                                           (request assemble-playbooks-url)
                                           (ltu/is-status 200)
                                           (ltu/body))

                ;; emergency will be disabled automatically, so we expect mgmt again
                reassembled-playbooks  (-> session-nuvlabox
                                           (request assemble-playbooks-url)
                                           (ltu/is-status 200)
                                           (ltu/body))]

            ;; and now emergency is re-disabled
            (-> session-nuvlabox
                (request (str p/service-context emergency-playbook-id))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :enabled false))

            (is (str/includes? assembled-playbooks "MGMT"))
            (is (str/includes? assembled-em-playbooks "EMERGENCY"))
            (is (str/includes? reassembled-playbooks "MGMT"))))))))


(deftest create-enable-host-level-management-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (j/write-value-as-string valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

            nuvlabox-url (str p/service-context nuvlabox-id)

            enable-url   (-> session-owner
                             (request nuvlabox-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-present :enable-host-level-management)
                             (ltu/is-operation-absent :disable-host-level-management)
                             (ltu/get-op-url :enable-host-level-management))]

        ;; confirm host-level mgmt is not enabled
        (-> session-owner
            (request nuvlabox-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :host-level-management-api-key nil))

        ;; enable it
        (-> session-owner
            (request enable-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/has-key :cronjob))

        ;; confirm host-level mgmt is now enabled, and api key exists
        (let [disable-url    (-> session-owner
                                 (request nuvlabox-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/is-operation-present :disable-host-level-management)
                                 (ltu/is-operation-absent :enable-host-level-management)
                                 (ltu/get-op-url :disable-host-level-management))

              credential-id  (-> session-owner
                                 (request nuvlabox-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 :response
                                 :body
                                 :host-level-management-api-key)

              credential-url (str p/service-context credential-id)]

          (-> session-owner
              (request credential-url)
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; disable it
          (-> session-owner
              (request disable-url
                       :request-method :post)
              (ltu/is-status 200))

          ;; confirm api key is gone
          (-> session-owner
              (request credential-url)
              (ltu/body->edn)
              (ltu/is-status 404))

          (-> session-owner
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :host-level-management-api-key nil)))))))


(deftest create-activate-generate-new-key-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (j/write-value-as-string valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

            nuvlabox-url (str p/service-context nuvlabox-id)

            activate-url (-> session-owner
                             (request nuvlabox-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-operation-present :generate-new-api-key)
                             (ltu/get-op-url :activate))]

        ;; activate nuvlabox
        (-> session
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [session-nuvlabox    (header session authn-info-header
                                          (str nuvlabox-id " " nuvlabox-id
                                               " group/nuvla-nuvlabox group/nuvla-anon"))

              generate-new-key    (-> session-nuvlabox
                                      (request nuvlabox-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/get-op-url :generate-new-api-key))

              credential-url      (-> session-owner
                                      (request generate-new-key
                                               :request-method :post)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-key-value (comp not str/blank?) :secret-key true)
                                      (ltu/body)
                                      :api-key
                                      (ltu/href->url))

              credential-nuvlabox (-> session-owner
                                      (request credential-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/is-key-value :parent nuvlabox-id)
                                      (ltu/body))

              claims              (:claims credential-nuvlabox)

              decommission-url    (-> session-owner
                                      (request nuvlabox-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/get-op-url :decommission))]

          ;; check ACL and claims of generated credential.
          (is (= (:identity claims) nuvlabox-id))
          (is (= (-> claims :roles set) #{nuvlabox-id
                                          "group/nuvla-user"
                                          "group/nuvla-anon"
                                          "group/nuvla-nuvlabox"}))

          ;; checks created api credential for NB visible for owner
          (-> session-owner
              (request credential-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-absent :edit)
              (ltu/is-operation-absent :delete))

          ;; decommission
          (-> session-owner
              (request decommission-url)
              (ltu/body->edn)
              (ltu/is-status 202))

          ;; op is no longer present
          (-> session-owner
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-absent :generate-new-api-key)))))))


(deftest create-activate-commission-get-context-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (j/write-value-as-string valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

            nuvlabox-url (str p/service-context nuvlabox-id)

            activate-url (-> session-owner
                             (request nuvlabox-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/get-op-url :activate))]

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [session-nuvlabox (header session authn-info-header
                                       (str nuvlabox-id " " nuvlabox-id
                                            " group/nuvla-nuvlabox group/nuvla-anon"))
              commission       (-> session-owner
                                   (request nuvlabox-url)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/get-op-url :commission))]

          (-> session-nuvlabox
              (request commission
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 200))

          (let [add-ssh-key      (-> session-owner
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/get-op-url :add-ssh-key))
                job-url          (-> session-owner
                                     (request add-ssh-key
                                              :request-method :post)
                                     (ltu/body->edn)
                                     (ltu/is-status 202)
                                     (ltu/location-url))

                get-context-url  (-> session-nuvlabox
                                     (request job-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :get-context)
                                     (ltu/get-op-url :get-context))

                get-context-body (-> session-nuvlabox
                                     (request get-context-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/body))]

            (is (and (map? get-context-body)
                     (str/starts-with? (namespace (ffirst get-context-body)) "credential")
                     (str/starts-with? (:public-key (second (first get-context-body)))
                                       "ssh-rsa AAAA")))

            (-> session-admin
                (request get-context-url)
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session-owner
                (request get-context-url)
                (ltu/body->edn)
                (ltu/is-status 403))))))))


(deftest create-activate-commission-suspend-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (j/write-value-as-string valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))

            nuvlabox-url (str p/service-context nuvlabox-id)

            activate-url (-> session-owner
                             (request nuvlabox-url)
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/get-op-url :activate))]

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [session-nuvlabox (header session authn-info-header
                                       (str nuvlabox-id " " nuvlabox-id
                                            " group/nuvla-nuvlabox group/nuvla-anon"))
              commission       (-> session-owner
                                   (request nuvlabox-url)
                                   (ltu/body->edn)
                                   (ltu/is-status 200)
                                   (ltu/get-op-url :commission))]

          (-> session-nuvlabox
              (request commission
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-owner
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string {"state" "SUSPENDED"}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "COMMISSIONED"))

          (-> session-admin
              (request nuvlabox-url
                       :request-method :put
                       :body (j/write-value-as-string {"state" "SUSPENDED"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          (let [unsuspend-url (-> session-owner
                                  (request nuvlabox-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-key-value :state "SUSPENDED")
                                  (ltu/is-operation-absent :activate)
                                  (ltu/is-operation-absent :enable-host-level-management)
                                  (ltu/is-operation-absent :commission)
                                  (ltu/is-operation-present :unsuspend)
                                  (ltu/is-operation-present :decommission)
                                  (ltu/get-op-url :unsuspend))]
            (testing "owner will not able to unsuspend when payment is required"
              (with-redefs [utils/throw-when-payment-required (fn [_req]
                                                                (throw (r/ex-response "" 402)))]
                (-> session-owner
                    (request unsuspend-url)
                    (ltu/body->edn)
                    (ltu/is-status 402))))

            ;; owner will be able to unsuspend when no exception thrown
            (-> session-owner
                (request unsuspend-url)
                (ltu/body->edn)
                (ltu/is-status 200))

            (-> session-owner
                (request nuvlabox-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :state "COMMISSIONED"))))))))

(deftest create-activate-commission-heartbeat-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")
          nuvlabox-id   (-> session-owner
                            (request base-uri
                                     :request-method :post
                                     :body (j/write-value-as-string
                                             (update valid-nuvlabox
                                                     :capabilities
                                                     conj
                                                     utils/capability-heartbeat)))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))

          nuvlabox-url  (str p/service-context nuvlabox-id)

          activate-url  (-> session-owner
                            (request nuvlabox-url)
                            (ltu/body->edn)
                            (ltu/is-status 200)
                            (ltu/get-op-url :activate))]

      ;; activate nuvlabox
      (-> session-anon
          (request activate-url
                   :request-method :post)
          (ltu/body->edn)
          (ltu/is-status 200))

      (let [session-nuvlabox (header session authn-info-header
                                     (str nuvlabox-id " " nuvlabox-id
                                          " group/nuvla-nuvlabox group/nuvla-anon"))
            commission       (-> session-owner
                                 (request nuvlabox-url)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/get-op-url :commission)
                                 (ltu/is-operation-absent :heartbeat))]

        (-> session-nuvlabox
            (request commission
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [nuvlabox-status-id (-> session-owner
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     ltu/body
                                     :nuvlabox-status)
              heartbeat-op       (-> session-nuvlabox
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present utils/action-heartbeat)
                                     (ltu/is-operation-absent utils/action-set-offline)
                                     (ltu/is-key-value :online nil)
                                     (ltu/get-op-url utils/action-heartbeat))
              set-offline-op     (-> session-admin
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present utils/action-heartbeat)
                                     (ltu/is-operation-present utils/action-set-offline)
                                     (ltu/get-op-url utils/action-set-offline))]
          (let [nb-status (db/retrieve nuvlabox-status-id)]
            (is (nil? (:online nb-status)))
            (is (nil? (:online-prev nb-status)))
            (is (nil? (:next-heartbeat nb-status)))
            (is (nil? (:last-heartbeat nb-status))))

          (-> session-nuvlabox
              (request heartbeat-op)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :jobs [])
              (ltu/is-key-value some? :doc-last-updated true))

          (-> session-nuvlabox
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online true))

          (let [nb-status (db/retrieve nuvlabox-status-id)]
            (is (true? (:online nb-status)))
            (is (nil? (:online-prev nb-status)))
            (is (some? (:next-heartbeat nb-status)))
            (is (some? (:last-heartbeat nb-status))))

          (testing "second heartbeat is possible and doesn't fail (noop on ES)"
            (-> session-nuvlabox
                (request heartbeat-op)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/is-key-value :jobs [])))

          (-> session-admin
              (request set-offline-op)
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-nuvlabox
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :online false))

          (let [nb-status (db/retrieve nuvlabox-status-id)]
            (is (false? (:online nb-status)))
            (is (true? (:online-prev nb-status)))
            (is (some? (:next-heartbeat nb-status)))
            (is (some? (:last-heartbeat nb-status))))
          )))))


(deftest should-propagate-changes-test
  (are [expected current updated]
    (= expected (nb/should-propagate-changes? current updated))
    true {:acl 1} {:acl 2}
    false {:acl 1} {}
    false {:acl 1} {:acl 1}
    true {:acl {:edit-data ["user/alpha"],
                :owners    ["group/nuvla-admin"],
                :view-acl  ["user/alpha"],
                :delete    ["user/alpha"],
                :view-meta ["nuvlabox/id" "user/alpha"],
                :edit-acl  ["user/alpha"],
                :view-data ["nuvlabox/id" "user/alpha"],
                :manage    ["nuvlabox/id" "user/alpha"],
                :edit-meta ["user/alpha"]}} {:acl {:edit-data ["user/alpha"],
                                                   :owners    ["group/nuvla-admin"],
                                                   :view-acl  ["user/alpha"],
                                                   :delete    ["user/alpha"],
                                                   :view-meta ["nuvlabox/id" "user/alpha"],
                                                   :edit-acl  [],
                                                   :view-data ["nuvlabox/id" "user/alpha"],
                                                   :manage    ["nuvlabox/id" "user/alpha"],
                                                   :edit-meta ["user/alpha"]}}
    false {:acl 1, :capabilities []} {:acl 1, :capabilities []}
    false {:acl 1, :capabilities []} {:acl 1}
    true {:acl 1, :capabilities []} {:acl 1 :capabilities ["a"]}
    true {:acl 1, :capabilities []} {:acl 2 :capabilities ["a"]}
    true {:acl 1, :capabilities ["a"], :name "x"} {:acl 1, :capabilities ["a"], :name "z"}
    true {} {:nuvlabox-status "nuvlabox-status"}
    false {:acl 1, :online true} {:acl 1, :online false}))

(deftest create-activate-commission-rename
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session             (-> (ltu/ring-app)
                                  session
                                  (content-type "application/json"))

          session-owner       (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon        (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

          nuvlabox-id         (-> session-owner
                                  (request base-uri
                                           :request-method :post
                                           :body (j/write-value-as-string valid-nuvlabox))
                                  (ltu/body->edn)
                                  (ltu/is-status 201)
                                  (ltu/location))

          nuvlabox-url        (str p/service-context nuvlabox-id)

          activate-url        (-> session-owner
                                  (request nuvlabox-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-key-value :state "NEW")
                                  (ltu/get-op-url :activate))

          session-nuvlabox    (header session authn-info-header
                                      (str nuvlabox-id " " nuvlabox-id
                                           " group/nuvla-nuvlabox group/nuvla-anon"))

          _                   (-> session-anon
                                  (request activate-url
                                           :request-method :post)
                                  (ltu/body->edn)
                                  (ltu/is-status 200))

          commission          (-> session-owner
                                  (request nuvlabox-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/get-op-url :commission))
          _                   (-> session-nuvlabox
                                  (request commission
                                           :request-method :post
                                           :body (j/write-value-as-string {:swarm-endpoint    "http://foo"
                                                                           :swarm-client-key  "key"
                                                                           :swarm-client-cert "cert"
                                                                           :swarm-client-ca   "ca"}))
                                  (ltu/body->edn)
                                  (ltu/is-status 200))
          swarm-credential-id (-> session-nuvlabox
                                  (request (str p/service-context "credential?filter=subtype='infrastructure-service-swarm'"))
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-count 1)
                                  ltu/body
                                  :resources
                                  first
                                  :id)
          module-id           (resource-creation/create-module session-owner)
          dep-1-id            (resource-creation/create-deployment session-owner module-id)
          dep-2-id            (resource-creation/create-deployment session-owner module-id)]

      (-> session-owner
          (request (str p/service-context dep-2-id)
                   :request-method :put
                   :body (j/write-value-as-string {:parent swarm-credential-id}))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :nuvlabox-name "nb-test"))

      (-> session-owner
          (request nuvlabox-url
                   :request-method :put
                   :body (j/write-value-as-string {:name "new-name"}))
          (ltu/body->edn)
          (ltu/is-status 200))

      (testing "deployment nuvlabox name was updated accordingly to the new name"
        (-> session-owner
            (request (str p/service-context dep-2-id)
                     :request-method :put
                     :body (j/write-value-as-string {:parent swarm-credential-id}))
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :nuvlabox-name "new-name")))

      (-> session-owner
          (request (str p/service-context dep-1-id))
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-key-value :nuvlabox-name nil)))))

(defn create-ne
  [session-owner nb-name]
  (-> session-owner
      (request base-uri
               :request-method :post
               :body (j/write-value-as-string (assoc valid-nuvlabox :name nb-name)))
      (ltu/body->edn)
      (ltu/is-status 201)
      ltu/location-url))

(defn- create-edit-nb-bulk-edit-tags-lifecycle-test
  [session-owner {nb-name :name
                  nb-tags :tags}]
  (let [ne-url (create-ne session-owner nb-name)]
    (-> session-owner
        (request ne-url
                 :request-method :put
                 :body (j/write-value-as-string {:tags nb-tags}))
        (ltu/body->edn)
        (ltu/is-status 200)
        (ltu/is-key-value :name nb-name)
        (ltu/is-key-value :tags nb-tags))
    ne-url))

(defn- set-up-bulk-edit-tags-lifecycle-test
  [session-owner nuvla-edges]
  (mapv (partial create-edit-nb-bulk-edit-tags-lifecycle-test session-owner)
        nuvla-edges))

(defn- run-bulk-edit-test!
  [{:keys [name endpoint filter tags expected-fn]}]
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
        ne-urls       (set-up-bulk-edit-tags-lifecycle-test
                        session-owner
                        [{:name "NE1"}
                         {:name "NE2" :tags ["foo"]}
                         {:name "NE3" :tags ["foo" "bar"]}])]
    (testing name
      (-> session-owner
          (header "bulk" "yes")
          (request endpoint
                   :request-method :patch
                   :body (j/write-value-as-string (cond-> {:doc {:tags tags}}
                                                          filter (assoc :filter filter))))
          (ltu/is-status 200))
      (run!
        (fn [url]
          (let [ne (-> session-owner
                       (request url)
                       (ltu/body->edn))]
            (testing (:name ne)
              (ltu/is-key-value ne :tags (expected-fn (-> ne :response :body))))))
        ne-urls))))

(def endpoint-set-tags (str base-uri "/" "set-tags"))
(def endpoint-add-tags (str base-uri "/" "add-tags"))
(def endpoint-remove-tags (str base-uri "/" "remove-tags"))

(deftest bulk-set-all-tags
  (run-bulk-edit-test! {:endpoint    endpoint-set-tags
                        :test-name   "Set all"
                        :tags        ["baz"]
                        :expected-fn (constantly ["baz"])}))

(deftest bulk-set-tags-on-subset
  (run-bulk-edit-test! {:endpoint    endpoint-set-tags
                        :filter      "(name='NE1') or (name='NE2')"
                        :test-name   "Set just 2"
                        :tags        ["foo" "bar" "baz"]
                        :expected-fn (fn [ne]
                                       (case (:name ne)
                                         "NE3" ["foo" "bar"]
                                         ["foo" "bar" "baz"]))}))

(deftest bulk-remove-all-tags
  (run-bulk-edit-test! {:endpoint    endpoint-set-tags
                        :test-name   "Remove all tags for all edges"
                        :tags        []
                        :expected-fn (constantly [])}))

(deftest bulk-remove-one-specific-tag
  (run-bulk-edit-test! {:endpoint    endpoint-remove-tags
                        :test-name   "Remove specific tags for all edges"
                        :tags        ["foo"]
                        :expected-fn (fn [ne] (case (:name ne)
                                                "NE3" ["bar"]
                                                []))}))

(deftest bulk-remove-multiple-specific-tags
  (run-bulk-edit-test! {:endpoint    endpoint-remove-tags
                        :test-name   "Remove specific tags for all edges"
                        :tags        ["foo" "bar"]
                        :expected-fn (constantly [])}))

(deftest bulk-add-tags
  (run-bulk-edit-test! {:endpoint    endpoint-add-tags
                        :test-name   "Add specific tags to current tags for all edges"
                        :tags        ["bar" "baz"]
                        :expected-fn (fn [ne]
                                       (case (:name ne)
                                         "NE1" ["bar" "baz"]
                                         ["foo" "bar" "baz"]))}))

(deftest bulk-update
  (let [session         (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
        session-owner   (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
        bulk-update-url (str base-uri "/" "bulk-update")]
    (-> session-owner
        (header "bulk" "yes")
        (request bulk-update-url
                 :request-method :patch
                 :body (j/write-value-as-string {:filter "tag='foobar'"}))
        (ltu/is-status 202))))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
