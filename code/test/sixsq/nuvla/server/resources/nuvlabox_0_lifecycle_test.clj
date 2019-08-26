(ns sixsq.nuvla.server.resources.nuvlabox-0-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest is use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.nuvlabox-0 :as nb-0]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb/resource-type))


(def isg-collection-uri (str p/service-context isg/resource-type))


(def infra-service-collection-uri (str p/service-context infra-service/resource-type))


(def credential-collection-uri (str p/service-context credential/resource-type))


(def nb-status-collection-uri (str p/service-context nb-status/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def user "jane")


(def nuvlabox-owner "user/alpha")


(def valid-nuvlabox {:created          timestamp
                     :updated          timestamp
                     :acl              {:owners   ["group/nuvla-admin" nuvlabox-owner]
                                        :view-acl ["user/jane"]
                                        :manage   ["user/jane"]}

                     ;; This is not the default version, so it must appear explicitly.
                     :version          0

                     :owner            nuvlabox-owner
                     :organization     "ACME"
                     :os-version       "OS version"
                     :hw-revision-code "a020d3"
                     :login-username   "aLoginName"
                     :login-password   "aLoginPassword"

                     :form-factor      "Nuvlabox"
                     :vm-cidr          "10.0.0.0/24"
                     :lan-cidr         "10.0.1.0/24"})


(deftest check-metadata
  (mdtu/check-metadata-exists nb/resource-type
                              (str nb/resource-type "-" nb-0/schema-version)))


(deftest create-delete-lifecycle
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        session-owner (header session authn-info-header "user/alpha group/nuvla-user group/nuvla-anon")]

    (doseq [session [session-admin session-owner]]
      (let [nuvlabox-id  (-> session
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))
            nuvlabox-url (str p/service-context nuvlabox-id)

            {:keys [id acl]} (-> session
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
        (is (contains? (set (:owners acl)) nuvlabox-owner))
        (is (contains? (set (:manage acl)) id))
        (is (contains? (set (:edit-acl acl)) "group/nuvla-admin"))

        (-> session
            (request nuvlabox-url
                     :request-method :delete)
            (ltu/is-status 200))))))


(deftest create-activate-decommission-delete-lifecycle
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        session-owner (header session authn-info-header "user/alpha group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "unknown group/nuvla-anon")]

    (doseq [session [session-admin session-owner]]
      (let [nuvlabox-id  (-> session
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-nuvlabox))
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

        (let [{:keys [owner acl]} (-> session
                                      (request nuvlabox-url)
                                      (ltu/body->edn)
                                      (ltu/is-status 200)
                                      (ltu/body))]

          ;; checks of acl for created credential
          (is (= [owner] (:owners acl)))
          (is (contains? (set (:manage acl)) nuvlabox-id))
          (is (contains? (set (:view-data acl)) nuvlabox-id))
          (is (not (contains? (set (:view-acl acl)) nuvlabox-id)))
          (is (not (contains? (set (:edit-meta acl)) nuvlabox-id)))
          (is (not (contains? (set (:delete acl)) nuvlabox-id))))

        ;; anonymous should be able to activate the NuvlaBox
        ;; and receive an api key/secret pair to access Nuvla
        (let [credential-url (-> session-anon
                                 (request activate-url
                                          :request-method :post)
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/is-key-value (comp not str/blank?) :secret-key true)
                                 (ltu/body)
                                 :api-key
                                 (ltu/href->url))

              {:keys [acl] :as credential-nuvlabox} (-> session-admin
                                                        (request credential-url)
                                                        (ltu/body->edn)
                                                        (ltu/is-status 200)
                                                        (ltu/is-key-value :parent nuvlabox-id)
                                                        (ltu/body))

              claims         (:claims credential-nuvlabox)

              {:keys [owner]} (-> session-admin
                                  (request nuvlabox-url)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/body))]

          ;; check ACL and claims of generated credential.
          (is (= (:identity claims) nuvlabox-id))
          (is (= (-> claims :roles set) #{nuvlabox-id
                                          "group/nuvla-user"
                                          "group/nuvla-anon"}))

          ;; checks of acl for created credential
          (is (= ["group/nuvla-admin"] (:owners acl)))
          (is (contains? (set (:delete acl)) owner))
          (is (contains? (set (:view-meta acl)) owner))
          (is (not (contains? (set (:view-meta acl)) nuvlabox-id)))
          (is (not (contains? (set (:view-data acl)) owner)))

          ;; verify that an infrastructure-service-group has been created for this nuvlabox
          (let [{:keys [acl]} (-> session-admin
                                  (content-type "application/x-www-form-urlencoded")
                                  (request isg-collection-uri
                                           :request-method :put
                                           :body (rc/form-encode {:filter (format "parent='%s'" nuvlabox-id)}))
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-count 1)
                                  (ltu/entries)
                                  first)]

            (is (= ["group/nuvla-admin"] (:owners acl)))
            (is (contains? (set (:view-meta acl)) owner))
            (is (not (contains? (set (:edit-meta acl)) owner))))

          ;; verify that an nuvlabox-status has been created for this nuvlabox
          (let [{:keys [acl]} (-> session-admin
                                  (content-type "application/x-www-form-urlencoded")
                                  (request nb-status-collection-uri
                                           :request-method :put
                                           :body (rc/form-encode {:filter (format "parent='%s'" nuvlabox-id)}))
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-count 1)
                                  (ltu/entries)
                                  first)]

            (is (= ["group/nuvla-admin"] (:owners acl)))
            (is (contains? (set (:edit-meta acl)) nuvlabox-id))
            (is (not (contains? (set (:edit-acl acl)) nuvlabox-id)))
            (is (contains? (set (:view-acl acl)) owner))
            (is (not (contains? (set (:edit-meta acl)) owner)))))

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

          (-> session
              (request decommission-url
                       :request-method :post)
              (ltu/body->edn)
              (ltu/is-status 202))

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
                     :body (json/write-str (assoc valid-nuvlabox :state "DECOMMISSIONED")))
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; DECOMMISSIONED state with correct actions
        (-> session
            (request nuvlabox-url)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-operation-present :edit)
            (ltu/is-operation-present :delete)
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

        ;; verify that the nuvlabox has been removed
        (-> session
            (request nuvlabox-url)
            (ltu/body->edn)
            (ltu/is-status 404))))))


(deftest create-activate-commission-decommission-error-delete-lifecycle
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-admin (header session authn-info-header "user/super group/nuvla-admin group/nuvla-user group/nuvla-anon")

        session-owner (header session authn-info-header "user/alpha group/nuvla-user group/nuvla-anon")
        session-anon  (header session authn-info-header "unknown group/nuvla-anon")]

    (doseq [session [session-admin session-owner]]
      (let [nuvlabox-id  (-> session
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-nuvlabox))
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

        (let [{isg-id :id} (-> session-admin
                               (content-type "application/x-www-form-urlencoded")
                               (request isg-collection-uri
                                        :request-method :put
                                        :body (rc/form-encode {:filter (format "parent='%s'" nuvlabox-id)}))
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
                       :body (json/write-str {:swarm-token-worker  "abc"
                                              :swarm-token-manager "def"
                                              ;:swarm-client-key    "key"
                                              ;:swarm-client-cert   "cert"
                                              ;:swarm-client-ca     "ca"
                                              :swarm-endpoint      "https://swarm.example.com"
                                              :minio-access-key    "access"
                                              :minio-secret-key    "secret"
                                              :minio-endpoint      "https://minio.example.com"}))
              (ltu/body->edn)
              (ltu/is-status 200))

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
              (ltu/is-key-value :state "COMMISSIONED"))

          ;; check that services exist
          (let [services (-> session
                             (content-type "application/x-www-form-urlencoded")
                             (request infra-service-collection-uri
                                      :request-method :put
                                      :body (rc/form-encode {:filter (format "parent='%s'" isg-id)}))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-count 2)
                             (ltu/entries))]

            (is (= #{"swarm" "s3"} (set (map :subtype services))))

            (doseq [{:keys [acl]} services]
              (is (= [nuvlabox-owner] (:owners acl))))

            (doseq [{:keys [subtype] :as service} services]
              (let [creds (-> session-admin
                              (content-type "application/x-www-form-urlencoded")
                              (request credential-collection-uri
                                       :request-method :put
                                       :body (rc/form-encode {:filter (format "parent='%s'" (:id service))}))
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/entries))]

                ;; all creds must be owned by the NuvlaBox owner
                (doseq [{:keys [acl]} creds]
                  (is (= [nuvlabox-owner] (:owners acl))))

                (if (= "swarm" subtype)
                  (is (= 2 (count creds))))                 ;; only swarm token credentials

                (if (= "s3" subtype)
                  (is (= 1 (count creds))))                 ;; only key/secret pair

                )))


          ;; second commissioning of the resource (with swarm credentials)
          (-> session
              (request commission
                       :request-method :post
                       :body (json/write-str {:swarm-token-worker  "abc"
                                              :swarm-token-manager "def"
                                              :swarm-client-key    "key"
                                              :swarm-client-cert   "cert"
                                              :swarm-client-ca     "ca"
                                              :swarm-endpoint      "https://swarm.example.com"
                                              :minio-access-key    "access"
                                              :minio-secret-key    "secret"
                                              :minio-endpoint      "https://minio.example.com"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; check the services again
          (let [services (-> session
                             (content-type "application/x-www-form-urlencoded")
                             (request infra-service-collection-uri
                                      :request-method :put
                                      :body (rc/form-encode {:filter (format "parent='%s'" isg-id)}))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-count 2)
                             (ltu/entries))]

            (is (= #{"swarm" "s3"} (set (map :subtype services))))

            (doseq [{:keys [acl]} services]
              (is (= [nuvlabox-owner] (:owners acl))))

            (doseq [{:keys [subtype] :as service} services]
              (let [creds (-> session-admin
                              (content-type "application/x-www-form-urlencoded")
                              (request credential-collection-uri
                                       :request-method :put
                                       :body (rc/form-encode {:filter (format "parent='%s'" (:id service))}))
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/entries))]

                ;; all creds must be owned by the NuvlaBox owner
                (doseq [{:keys [acl]} creds]
                  (is (= [nuvlabox-owner] (:owners acl))))

                (if (= "swarm" subtype)
                  (is (= 3 (count creds))))                 ;; now both tokens and credential

                (if (= "s3" subtype)
                  (is (= 1 (count creds))))                 ;; only key/secret pair

                )))

          ;; third commissioning of the resource make sure no additional credentials created
          (-> session
              (request commission
                       :request-method :post
                       :body (json/write-str {:swarm-token-worker  "abc"
                                              :swarm-token-manager "def"
                                              :swarm-client-key    "key-bad"
                                              :swarm-client-cert   "cert-bad"
                                              :swarm-client-ca     "ca-bad"
                                              :swarm-endpoint      "https://swarm.example.com"
                                              :minio-access-key    "access"
                                              :minio-secret-key    "secret"
                                              :minio-endpoint      "https://minio.example.com"}))
              (ltu/body->edn)
              (ltu/is-status 200))

          ;; check the services again
          (let [services (-> session
                             (content-type "application/x-www-form-urlencoded")
                             (request infra-service-collection-uri
                                      :request-method :put
                                      :body (rc/form-encode {:filter (format "parent='%s'" isg-id)}))
                             (ltu/body->edn)
                             (ltu/is-status 200)
                             (ltu/is-count 2)
                             (ltu/entries))]

            (is (= #{"swarm" "s3"} (set (map :subtype services))))

            (doseq [{:keys [acl]} services]
              (is (= [nuvlabox-owner] (:owners acl))))

            (doseq [{:keys [subtype] :as service} services]
              (let [creds (-> session-admin
                              (content-type "application/x-www-form-urlencoded")
                              (request credential-collection-uri
                                       :request-method :put
                                       :body (rc/form-encode {:filter (format "parent='%s'" (:id service))}))
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/entries))]

                ;; all creds must be owned by the NuvlaBox owner
                (doseq [{:keys [acl]} creds]
                  (is (= [nuvlabox-owner] (:owners acl))))

                (if (= "swarm" subtype)
                  (is (= 3 (count creds))))                 ;; now both tokens and credential

                (if (= "s3" subtype)
                  (is (= 1 (count creds))))                 ;; only key/secret pair

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
                     :body (json/write-str {:state "DECOMMISSIONED"}))
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
                     :body (json/write-str {:state "ERROR"}))
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
            (ltu/is-status 200))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
