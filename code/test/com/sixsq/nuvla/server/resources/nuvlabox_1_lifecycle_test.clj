(ns com.sixsq.nuvla.server.resources.nuvlabox-1-lifecycle-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [are deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.configuration :as configuration]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.configuration-template :as configuration-tpl]
    [com.sixsq.nuvla.server.resources.configuration-template-vpn-api :as configuration-tpl-vpn]
    [com.sixsq.nuvla.server.resources.credential :as credential]
    [com.sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [com.sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [com.sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [com.sixsq.nuvla.server.resources.infrastructure-service-template-vpn :as infra-srvc-tpl-vpn]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.resources.nuvlabox :as nb]
    [com.sixsq.nuvla.server.resources.nuvlabox-1 :as nb-1]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb/resource-type))


(def isg-collection-uri (str p/service-context isg/resource-type))


(def infra-service-collection-uri (str p/service-context infra-service/resource-type))


(def credential-collection-uri (str p/service-context credential/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox-owner "user/alpha")


(def valid-nuvlabox {:created          timestamp
                     :updated          timestamp

                     :version          1

                     :organization     "ACME"
                     :os-version       "OS version"
                     :hw-revision-code "a020d3"
                     :login-username   "aLoginName"
                     :login-password   "aLoginPassword"

                     :form-factor      "Nuvlabox"
                     :vm-cidr          "10.0.0.0/24"
                     :lan-cidr         "10.0.1.0/24"
                     :ssh-keys         ["credential/aaa-bbb-ccc"]
                     :capabilities     ["RANDOM" "NUVLA_JOB_PULL"]})


(deftest check-metadata
  (mdtu/check-metadata-exists nb/resource-type
                              (str nb/resource-type "-" nb-1/schema-version)))

(def admin-group-name "Nuvla Administrator Group")


(deftest create-edit-delete-lifecycle
  ;; Disable stripe
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          authn-info    {:user-id      "user/alpha"
                         :active-claim "user/alpha"
                         :claims       ["group/nuvla-anon" "user/alpha" "group/nuvla-user"]}]

      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (j/write-value-as-string valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))
            nuvlabox-url (str p/service-context nuvlabox-id)

            _            (ltu/is-last-event nuvlabox-id
                                            {:name               "nuvlabox.add"
                                             :description        (str "user/alpha added nuvlabox " nuvlabox-id)
                                             :category           "add"
                                             :success            true
                                             :linked-identifiers []
                                             :authn-info         authn-info
                                             :acl                {:owners ["group/nuvla-admin" "user/alpha"]}})
            {:keys [id acl owner]} (-> session-owner
                                       (request nuvlabox-url)
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

        (-> session-owner
            (request nuvlabox-url
                     :request-method :delete)
            (ltu/is-status 200)))

      ;; create nuvlabox with inexistent vpn id will fail
      (-> session-owner
          (request base-uri
                   :request-method :post
                   :body (j/write-value-as-string (assoc valid-nuvlabox
                                           :vpn-server-id "infrastructure-service/fake")))
          (ltu/body->edn)
          (ltu/is-status 404))
      )))


(deftest create-activate-decommission-delete-lifecycle
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
                                               :description        (str user-name-or-id " added nuvlabox " nuvlabox-id)
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

          (let [decommission-url (-> session
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :edit)
                                     (ltu/is-operation-absent :delete)
                                     (ltu/is-operation-absent :activate)
                                     (ltu/is-operation-present :commission)
                                     (ltu/is-operation-present :decommission)
                                     (ltu/is-key-value :state "ACTIVATED")
                                     (ltu/get-op-url :decommission))]

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
                              :description        (str user-name-or-id " deleted nuvlabox " nuvlabox-id)
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

              _            (ltu/is-last-event nuvlabox-id
                                              {:name               "nuvlabox.add"
                                               :description        (str user-name-or-id " added nuvlabox " nuvlabox-id)
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
                                                :capabilities        ["NUVLA_JOB_PULL"]}))
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
                    (is (= 1 (count creds))))               ;; only key/secret pair

                  )))


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
                    (is (= 1 (count creds))))
                  ))))

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

          ;; commissioning of the nuvlabox
          (-> session-owner
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
                  (is (= 1 (count creds))))
                )))

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
                  (is (= 1 (count creds))))
                )))

          )))))


(deftest create-activate-commission-removed-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "unknown unknown group/nuvla-anon")]

      #_{:clj-kondo/ignore [:redundant-let]}
      (let [nuvlabox-id      (-> session-owner
                                 (request base-uri
                                          :request-method :post
                                          :body (j/write-value-as-string valid-nuvlabox))
                                 (ltu/body->edn)
                                 (ltu/is-status 201)
                                 (ltu/location))

            session-nuvlabox (header session authn-info-header
                                     (str nuvlabox-id
                                          " group/nuvla-nuvlabox group/nuvla-anon"))

            nuvlabox-url     (str p/service-context nuvlabox-id)

            activate-url     (-> session-owner
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

          ;; commissioning of the nuvlabox
          (-> session-nuvlabox
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
                  (is (= 1 (count creds))))
                )))

          ;; commissioning with removed
          (-> session-nuvlabox
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
                                              :kubernetes-endpoint    "https://k8s.example.com"
                                              :removed                ["swarm-token-manager"
                                                                       "swarm-token-worker"]}))
              (ltu/body->edn)
              (ltu/is-status 200))

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

            (is (= #{"s3" "kubernetes" "swarm"} (set (map :subtype services))))

            (doseq [{:keys [acl]} services]
              (is (= [nuvlabox-owner] (:view-acl acl))))

            (doseq [{:keys [subtype] :as service} ["swarm" "s3" "kubernetes"]]
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
                  (is (= 0 (count creds))))                 ;; swarm creds all gone

                (when (= "s3" subtype)
                  (is (= 1 (count creds))))                 ;; only key/secret pair

                (when (= "kubernetes" subtype)
                  (is (= 1 (count creds))))
                )))

          ;; additionnal call to commission will not fail
          (-> session-nuvlabox
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
                                              :kubernetes-endpoint    "https://k8s.example.com"
                                              :removed                ["swarm-endpoint"
                                                                       "swarm-token-manager"
                                                                       "swarm-token-worker"
                                                                       "swarm-client-key"]}))
              (ltu/body->edn)
              (ltu/is-status 200))

          )))))


(deftest create-activate-commission-vpn-lifecycle
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
                                    :response
                                    :body
                                    :resources
                                    first
                                    :endpoint)]
              (is (= srvc-endpoint "http://foo")))

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
                                    :response
                                    :body
                                    :resources
                                    first
                                    :endpoint)]
              (is (= srvc-endpoint "http://bar")))

            ))))))


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
                                    :edit-meta [nuvlabox-id "user/alpha"]})))

      )))


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
                (ltu/is-status 403)))

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


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
