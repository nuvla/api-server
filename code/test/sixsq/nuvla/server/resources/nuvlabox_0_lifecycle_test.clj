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
    [sixsq.nuvla.server.resources.configuration :as configuration]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.configuration-template :as configuration-tpl]
    [sixsq.nuvla.server.resources.configuration-template-vpn-api :as configuration-tpl-vpn]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential.vpn-utils :as vpn-utils]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [sixsq.nuvla.server.resources.infrastructure-service-template :as infra-service-tpl]
    [sixsq.nuvla.server.resources.infrastructure-service-template-vpn :as infra-srvc-tpl-vpn]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [sixsq.nuvla.server.resources.nuvlabox-0 :as nb-0]
    [sixsq.nuvla.server.resources.nuvlabox-release :as nuvlabox-release]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb/resource-type))


(def isg-collection-uri (str p/service-context isg/resource-type))


(def infra-service-collection-uri (str p/service-context infra-service/resource-type))


(def credential-collection-uri (str p/service-context credential/resource-type))

(def nuvlabox-release-collection-uri (str p/service-context nuvlabox-release/resource-type))

(def nb-status-collection-uri (str p/service-context nb-status/resource-type))


(def timestamp "1964-08-25T10:00:00Z")


(def user "jane")


(def nuvlabox-owner "user/alpha")


(def valid-nuvlabox {:created          timestamp
                     :updated          timestamp

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


(def valid-nb-rel {:release "1"
                   :url "url"
                   :pre-release false
                   :release-date "2020-12-03T11:23:18Z"
                   :compose-files [{:file "file"
                                    :name "name"
                                    :scope "scope"}]
                   :acl {
                         :view-data [
                                     "group/nuvla-user"
                                     ],
                         :view-meta [
                                     "group/nuvla-user"
                                     ],
                         :view-acl [
                                    "group/nuvla-user"
                                    ],
                         :owners [
                                  "group/nuvla-admin"
                                  ]
                         }})


(deftest check-metadata
  (mdtu/check-metadata-exists nb/resource-type
                              (str nb/resource-type "-" nb-0/schema-version)))


(deftest create-edit-delete-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha group/nuvla-user group/nuvla-anon")]

      (let [nuvlabox-id  (-> session-owner
                             (request base-uri
                                      :request-method :post
                                      :body (json/write-str valid-nuvlabox))
                             (ltu/body->edn)
                             (ltu/is-status 201)
                             (ltu/location))
            nuvlabox-url (str p/service-context nuvlabox-id)

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

        ;; only name description acl are editable for normal user other changes are ignored
        ;; FIXME update with test of change acl repercussion on other resources
        (let [new-name  "name NB changed"
              new-owner "user/beta"]
          (-> session-owner
              (request nuvlabox-url
                       :request-method :put
                       :body (json/write-str
                               {:name  new-name
                                :state "change is ignored"
                                :acl   (assoc acl :edit-acl (conj (:edit-acl acl) new-owner))}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "NEW")
              (ltu/is-key-value :name new-name)
              (ltu/is-key-value :edit-acl :acl (conj (:edit-acl acl) new-owner))
              (ltu/body)))

        (-> session-owner
            (request nuvlabox-url
                     :request-method :delete)
            (ltu/is-status 200)))

      ;; create nuvlabox with inexistent vpn id will fail
      (-> session-owner
          (request base-uri
                   :request-method :post
                   :body (json/write-str (assoc valid-nuvlabox
                                           :vpn-server-id "infrastructure-service/fake")))
          (ltu/body->edn)
          (ltu/is-status 404))
      )))


(deftest create-activate-decommission-delete-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")

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

          ;; verify that the nuvlabox has been removed
          (-> session
              (request nuvlabox-url)
              (ltu/body->edn)
              (ltu/is-status 404)))))))


(deftest create-activate-commission-decommission-error-delete-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")

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
                (ltu/is-operation-present :check-api)
                (ltu/is-operation-present :reboot)
                (ltu/is-key-value :state "COMMISSIONED"))

            ;; check that services exist
            (let [services (-> session
                               (content-type "application/x-www-form-urlencoded")
                               (request infra-service-collection-uri
                                        :request-method :put
                                        :body (rc/form-encode {:filter (format "parent='%s'"
                                                                               isg-id)}))
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

                  (if (= "swarm" subtype)
                    (is (= 2 (count creds))))               ;; only swarm token credentials

                  (if (= "s3" subtype)
                    (is (= 1 (count creds))))               ;; only key/secret pair

                  )))

            ;; check custom operations
            ;;
            (let [check-api      (-> session
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :edit)
                                     (ltu/is-operation-absent :delete)
                                     (ltu/is-operation-absent :activate)
                                     (ltu/is-operation-present :commission)
                                     (ltu/is-operation-present :decommission)
                                     (ltu/is-operation-present :check-api)
                                     (ltu/is-operation-present :reboot)
                                     (ltu/is-operation-present :add-ssh-key)
                                     (ltu/is-operation-present :revoke-ssh-key)
                                     (ltu/is-operation-present :update-nuvlabox)
                                     (ltu/is-key-value :state "COMMISSIONED")
                                     (ltu/get-op-url :check-api))

                  reboot         (-> session
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :edit)
                                     (ltu/is-operation-absent :delete)
                                     (ltu/is-operation-absent :activate)
                                     (ltu/is-operation-present :commission)
                                     (ltu/is-operation-present :decommission)
                                     (ltu/is-operation-present :check-api)
                                     (ltu/is-operation-present :reboot)
                                     (ltu/is-operation-present :add-ssh-key)
                                     (ltu/is-operation-present :revoke-ssh-key)
                                     (ltu/is-operation-present :update-nuvlabox)
                                     (ltu/is-key-value :state "COMMISSIONED")
                                     (ltu/get-op-url :reboot))
                  add-ssh-key    (-> session
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :edit)
                                     (ltu/is-operation-absent :delete)
                                     (ltu/is-operation-absent :activate)
                                     (ltu/is-operation-present :commission)
                                     (ltu/is-operation-present :decommission)
                                     (ltu/is-operation-present :check-api)
                                     (ltu/is-operation-present :reboot)
                                     (ltu/is-operation-present :add-ssh-key)
                                     (ltu/is-operation-present :revoke-ssh-key)
                                     (ltu/is-operation-present :update-nuvlabox)
                                     (ltu/is-key-value :state "COMMISSIONED")
                                     (ltu/get-op-url :add-ssh-key))

                  revoke-ssh-key (-> session
                                     (request nuvlabox-url)
                                     (ltu/body->edn)
                                     (ltu/is-status 200)
                                     (ltu/is-operation-present :edit)
                                     (ltu/is-operation-absent :delete)
                                     (ltu/is-operation-absent :activate)
                                     (ltu/is-operation-present :commission)
                                     (ltu/is-operation-present :decommission)
                                     (ltu/is-operation-present :check-api)
                                     (ltu/is-operation-present :reboot)
                                     (ltu/is-operation-present :add-ssh-key)
                                     (ltu/is-operation-present :revoke-ssh-key)
                                     (ltu/is-operation-present :update-nuvlabox)
                                     (ltu/is-key-value :state "COMMISSIONED")
                                     (ltu/get-op-url :revoke-ssh-key))

                  update-nuvlabox (-> session
                                    (request nuvlabox-url)
                                    (ltu/body->edn)
                                    (ltu/is-status 200)
                                    (ltu/is-operation-present :edit)
                                    (ltu/is-operation-absent :delete)
                                    (ltu/is-operation-absent :activate)
                                    (ltu/is-operation-present :commission)
                                    (ltu/is-operation-present :decommission)
                                    (ltu/is-operation-present :check-api)
                                    (ltu/is-operation-present :reboot)
                                    (ltu/is-operation-present :add-ssh-key)
                                    (ltu/is-operation-present :revoke-ssh-key)
                                    (ltu/is-operation-present :update-nuvlabox)
                                    (ltu/is-key-value :state "COMMISSIONED")
                                    (ltu/get-op-url :update-nuvlabox))

                  aux-ssh-cred   (-> session
                                     (request credential-collection-uri
                                              :request-method :post
                                              :body (json/write-str
                                                      {:template
                                                       {:href "credential-template/generate-ssh-key"}}))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/body)
                                     :resource-id)

                  nuvlabox-release (-> session-admin
                                     (request nuvlabox-release-collection-uri
                                       :request-method :post
                                       :body (json/write-str
                                               valid-nb-rel))
                                     (ltu/body->edn)
                                     (ltu/is-status 201)
                                     (ltu/body)
                                     :resource-id)]

              ;; check-api action
              (-> session
                  (request check-api)
                  (ltu/body->edn)
                  (ltu/is-status 202))

              ;; reboot action
              (-> session
                  (request reboot)
                  (ltu/body->edn)
                  (ltu/is-status 202))

              ;; add-ssh-key action
              (-> session
                  (request add-ssh-key)
                  (ltu/body->edn)
                  (ltu/is-status 202))

              ;; revoke-ssh-key action
              (-> session
                  (request revoke-ssh-key
                           :request-method :post
                           :body (json/write-str {:credential aux-ssh-cred}))
                  (ltu/body->edn)
                  (ltu/is-status 202))

              ;; update-nuvlabox-action
              (-> session
                  (request update-nuvlabox
                    :request-method :post
                    :body (json/write-str {:nuvlabox-release nuvlabox-release}))
                  (ltu/body->edn)
                  (ltu/is-status 202)))

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
                                                :minio-endpoint      "https://minio.example.com"
                                                :kubernetes-client-key    "key"
                                                :kubernetes-client-cert   "cert"
                                                :kubernetes-client-ca     "ca"
                                                :kubernetes-endpoint      "https://k8s.example.com"}))
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

              (is (= #{"swarm" "s3" "kubernetes"} (set (map :subtype services)))))

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

                  (if (= "swarm" subtype)
                    (is (= 3 (count creds))))               ;; now both tokens and credential

                  (if (= "s3" subtype)
                    (is (= 1 (count creds))))               ;; only key/secret pair

                  (if (= "kubernetes" subtype)
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
              (ltu/is-status 200)))))))


(deftest create-activate-commission-vpn-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "unknown group/nuvla-anon")]

      (let [infra-srvc-vpn-create {:template {:href      (str infra-service-tpl/resource-type "/"
                                                              infra-srvc-tpl-vpn/method)
                                              :vpn-scope "nuvlabox"
                                              :acl       {:owners   ["nuvla/admin"]
                                                          :view-acl ["nuvla/user"
                                                                     "nuvla/nuvlabox"]}}}
            infra-srvc-vpn-id     (-> session-admin
                                      (request (str p/service-context infra-service/resource-type)
                                               :request-method :post
                                               :body (json/write-str infra-srvc-vpn-create))
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
                                               :body (json/write-str
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
                     :body (json/write-str conf-vpn-create))
            (ltu/body->edn)
            (ltu/is-status 201))

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [session-nuvlabox  (header session authn-info-header
                                        (str nuvlabox-id
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
                         :body (json/write-str {:vpn-csr "foo"}))
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
                           :body (json/write-str {:vpn-csr "foo"}))
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
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "unknown group/nuvla-anon")]

      (let [infra-srvc-vpn-create {:template {:href      (str infra-service-tpl/resource-type "/"
                                                              infra-srvc-tpl-vpn/method)
                                              :vpn-scope "nuvlabox"
                                              :acl       {:owners   ["nuvla/admin"]
                                                          :view-acl ["nuvla/user"
                                                                     "nuvla/nuvlabox"]}}}
            infra-srvc-vpn-id     (-> session-admin
                                      (request (str p/service-context infra-service/resource-type)
                                               :request-method :post
                                               :body (json/write-str infra-srvc-vpn-create))
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
                                               :body (json/write-str
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
                     :body (json/write-str conf-vpn-create))
            (ltu/body->edn)
            (ltu/is-status 201))

        ;; activate nuvlabox
        (-> session-anon
            (request activate-url
                     :request-method :post)
            (ltu/body->edn)
            (ltu/is-status 200))

        (let [session-nuvlabox  (header session authn-info-header
                                        (str nuvlabox-id
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
                         :body (json/write-str {:swarm-endpoint "http://foo"}))
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
                         :body (json/write-str {:swarm-endpoint "http://bar"}))
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


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
