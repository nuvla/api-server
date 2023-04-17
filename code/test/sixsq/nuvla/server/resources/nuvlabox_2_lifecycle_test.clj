(ns sixsq.nuvla.server.resources.nuvlabox-2-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [are deftest is testing use-fixtures]]
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
    [sixsq.nuvla.server.resources.nuvlabox-2 :as nb-2]
    [sixsq.nuvla.server.resources.nuvlabox-playbook :as nb-playbook]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [sixsq.nuvla.server.util.response :as r]))

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
                     :capabilities     ["RANDOM" "NUVLA_JOB_PULL"]})


(deftest check-metadata
  (mdtu/check-metadata-exists nb/resource-type
                              (str nb/resource-type "-" nb-2/schema-version)))


(deftest create-edit-delete-lifecycle
  ;; Disable stripe
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")]

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
          (ltu/is-status 404)))))



(deftest create-activate-create-log-decommission-delete-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header (str "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon " session-id))
          session-owner (header session authn-info-header (str "user/alpha user/alpha group/nuvla-user group/nuvla-anon " session-id))
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")]

      (doseq [session [session-admin session-owner]]
        (let [nuvlabox-id  (-> session
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str (assoc valid-nuvlabox
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
                                       :body (json/write-str {:components ["agent" "security"]}))
                              (ltu/body->edn)
                              (ltu/is-status 201)
                              (ltu/location-url))]

              (testing "verify that the log resource exists and acl is owned by
              nuvlabox id and edit-acl is set for the session id"
                (-> session
                    (request log-url)
                    (ltu/body->edn)
                    (ltu/is-status 200)
                    (ltu/is-key-value :components ["agent" "security"])
                    (ltu/is-key-value :owners :acl [nuvlabox-id])
                    (ltu/is-key-value :delete :acl ["group/nuvla-admin" session-id])
                    (ltu/is-key-value :view-acl :acl ["group/nuvla-admin" session-id]))))

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
                                        :body (json/write-str (assoc valid-nuvlabox
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
                       :body (json/write-str (assoc nuvlabox :location location :supplier supplier)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :location location)
              (ltu/is-key-value :supplier (if (= session session-owner) nil supplier)))

          ;; admin and owner are able to delete location attribute
          (-> session
              (request (str nuvlabox-url "?select=location")
                       :request-method :put
                       :body (json/write-str (dissoc nuvlabox :location)))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :location nil)))))))


(deftest create-activate-commission-decommission-error-delete-lifecycle
  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                            session
                            (content-type "application/json"))
          session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")

          session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
          session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")
          tags          #{"tag-1", "tag-2"}]

      (doseq [session [session-admin session-owner]]
        (let [nuvlabox-id  (-> session
                               (request base-uri
                                        :request-method :post
                                        :body (json/write-str (assoc valid-nuvlabox
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
                         :body (json/write-str {:swarm-token-worker  "abc"
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
                (ltu/is-key-value set :tags tags))

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

              ;; tags is also applied to infra service swarm
              (is (= tags (->> services
                               (filter #(= (:subtype %) "swarm"))
                               first
                               :tags
                               set)))

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
                    (is (= 1 (count creds)))))))               ;; only key/secret pair





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
                           :body (json/write-str {:cluster-action "join-worker" :nuvlabox-manager-status {}}))
                  (ltu/body->edn)
                  (ltu/is-status 202)))


            ;; second commissioning of the resource (with swarm credentials)
            (-> session
                (request commission
                         :request-method :post
                         :body (json/write-str {:swarm-token-worker     "abc"
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
                         :body (json/write-str {:swarm-token-worker  "abc"
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
                                      :body (json/write-str valid-nuvlabox))
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
                       :body (json/write-str {:swarm-token-worker     "abc"
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
                       :body (json/write-str {:acl {:edit-acl [user-beta]}}))
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
                       :body (json/write-str {:acl {:edit-acl [user-beta]}}))
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
                                    (ltu/entries)
                                    first)]
              (is (= (:endpoint srvc-endpoint) "http://foo"))
              (is (= (:name srvc-endpoint) nb-name)))

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
                                     :body (json/write-str valid-nuvlabox))
                            (ltu/body->edn)
                            (ltu/is-status 201)
                            (ltu/location))
          nuvlabox-url  (str p/service-context nuvlabox-id)]

      (-> session-admin
          (request nuvlabox-url
                   :request-method :put
                   :body (json/write-str {:state "COMMISSIONED"}))
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
                                      :body (json/write-str valid-nuvlabox))
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
                     :body (json/write-str {:parent nuvlabox-id :type "MANAGEMENT" :run "foo MGMT" :enabled true}))
            (ltu/body->edn)
            (ltu/is-status 201))


        (let [session-nuvlabox (header session authn-info-header
                                       (str nuvlabox-id " " nuvlabox-id
                                            " group/nuvla-nuvlabox group/nuvla-anon"))]

          (let [;; create emergency playbook
                emergency-playbook-id  (-> session-owner
                                           (request playbook-base-uri
                                                    :request-method :post
                                                    :body (json/write-str {:parent nuvlabox-id :type "EMERGENCY" :run "foo EMERGENCY" :enabled false}))
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
                                                    :body (json/write-str {:emergency-playbooks-ids [emergency-playbook-id]}))
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
                                      :body (json/write-str valid-nuvlabox))
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
                                      :body (json/write-str valid-nuvlabox))
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
                                      :body (json/write-str valid-nuvlabox))
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
                                      :body (json/write-str valid-nuvlabox))
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
                       :body (json/write-str {"state" "SUSPENDED"}))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-key-value :state "COMMISSIONED"))

          (-> session-admin
              (request nuvlabox-url
                       :request-method :put
                       :body (json/write-str {"state" "SUSPENDED"}))
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

(defn- create-edit-nb-bulk-edit-tags-lifecycle-test
  [session-owner {nb-name :name
                  nb-tags :tags}]
  (let [ne-url (-> session-owner
                   (request base-uri
                            :request-method :post
                            :body (json/write-str (assoc valid-nuvlabox :name nb-name)))
                   (ltu/body->edn)
                   (ltu/is-status 201)
                   ((juxt ltu/location-url ltu/location)))]
    (-> session-owner
        (request (first ne-url)
                 :request-method :put
                 :body (json/write-str {:tags nb-tags}))
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
  [{:keys [name
           endpoint
           filter
           tags
           expected-fn]}]
  (let [session       (-> (ltu/ring-app)
                          session
                          (content-type "application/json"))
        session-owner (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
        ne-urls (set-up-bulk-edit-tags-lifecycle-test
                 session-owner
                 [{:name "NE1"}
                  {:name "NE2" :tags ["foo"]}
                  {:name "NE3" :tags ["foo" "bar"]}])]
    (testing name
      (-> session-owner
          (header "bulk" "yes")
          (request endpoint
                   :request-method :patch
                   :body (json/write-str {:filter filter
                                          :doc {:tags tags}}))
          (ltu/is-status 200))
      (run!
       (fn [[url]]
         (let [ne (-> session-owner
                      (request url)
                      (ltu/body->edn))]
           (testing (:name ne)
            (ltu/is-key-value ne :tags (expected-fn (-> ne :response :body))))))
       ne-urls))))

(deftest bulk-set-all-tags
  (run-bulk-edit-test! {:endpoint     (str base-uri "/" "set-tags")
                        :filter         "id!=null"
                        :test-name     "Set all"
                        :tags          ["baz"]
                        :expected-fn   (fn [_] ["baz"])}))

(deftest bulk-set-tags-on-subset
  (run-bulk-edit-test! {:endpoint     (str base-uri "/" "set-tags")
                        :filter         "(name='NE1') or (name='NE2')"
                        :test-name     "Set just 2"
                        :tags          ["foo" "bar" "baz"]
                        :expected-fn   (fn [ne]
                                         (case (:name ne)
                                           "NE3" ["foo" "bar"]
                                           ["foo" "bar" "baz"]))}))

(deftest bulk-remove-all-tags
  (run-bulk-edit-test! {:endpoint     (str base-uri "/" "set-tags")
                        :filter         "id!=null"
                        :test-name     "Remove all tags for all edges"
                        :tags          []
                        :expected-fn   (fn [_] [])}))

(deftest bulk-remove-one-specific-tag
  (run-bulk-edit-test! {:endpoint     (str base-uri "/" "remove-tags")
                        :filter         "id!=null"
                        :test-name     "Remove specific tags for all edges"
                        :tags          ["foo"]
                        :expected-fn   (fn [ne] (case (:name ne) "NE3" ["bar"] []))}))

(deftest bulk-remove-multiple-specific-tags
  (run-bulk-edit-test! {:endpoint     (str base-uri "/" "remove-tags")
                        :filter         "id!=null"
                        :test-name     "Remove specific tags for all edges"
                        :tags          ["foo" "bar"]
                        :expected-fn   (fn [_] [])}))

(deftest bulk-add-tags
  (run-bulk-edit-test! {:endpoint     (str base-uri "/" "add-tags")
                        :filter         "id!=null"
                        :test-name     "Add specific tags to current tags for all edges"
                        :tags          ["bar" "baz"]
                        :expected-fn   (fn [ne]
                                         (case (:name ne)
                                           "NE1" ["bar" "baz"]
                                           ["foo" "bar" "baz"]))}))

(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))

(deftest bulk-edit-tags-test-fail-test
  (let [session            (-> (ltu/ring-app)
                               session
                               (content-type "application/json"))
        session-owner      (header session authn-info-header "user/alpha user/alpha group/nuvla-user group/nuvla-anon")
        session-owner-bulk (header session-owner "bulk" "yes")
        endpoints          (mapv #(str base-uri "/" %) ["set-tags" "remove-tags" "add-tags"])
        check-error        #(-> %
                                (ltu/is-status 400)
                                (ltu/body)
                                (json/read-str)
                                (get "message"))]
    (run!
      (fn [endpoint]
        (is (= "Bulk request should contain bulk http header."
               (-> session-owner
                   (request endpoint :request-method :patch)
                   check-error))))
      endpoints)

    (run!
      (fn [endpoint]
        (is (= "No valid update data provided."
               (-> session-owner-bulk
                   (request endpoint :request-method :patch)
                   check-error))))
      endpoints)))