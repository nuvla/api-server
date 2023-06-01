(ns sixsq.nuvla.server.resources.module-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [deftest is testing use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.module :as module]
    [sixsq.nuvla.server.resources.module.utils :as utils]))

(use-fixtures :each ltu/with-test-server-fixture)

(def base-uri (str p/service-context module/resource-type))

(def timestamp "1964-08-25T10:00:00.00Z")

(defn lifecycle-test-module
  [subtype valid-content]
  (let [session-anon  (-> (session (ltu/ring-app))
                          (content-type "application/json"))
        session-admin (header session-anon authn-info-header
                              "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
        session-user  (header session-anon authn-info-header
                              "user/jane user/jane group/nuvla-user group/nuvla-anon")

        valid-entry   {:parent-path               "a/b"
                       :path                      "a/b/c"
                       :subtype                   subtype

                       :compatibility             "docker-compose"

                       :logo-url                  "https://example.org/logo"

                       :data-accept-content-types ["application/json" "application/x-something"]
                       :data-access-protocols     ["http+s3" "posix+nfs"]

                       :content                   valid-content}]

    ;; create: NOK for anon
    (-> session-anon
        (request base-uri
                 :request-method :post
                 :body (json/write-str valid-entry))
        (ltu/body->edn)
        (ltu/is-status 403))

    ;; queries: NOK for anon
    (-> session-anon
        (request base-uri)
        (ltu/body->edn)
        (ltu/is-status 403))

    (doseq [session [session-admin session-user]]
      (-> session
          (request base-uri)
          (ltu/body->edn)
          (ltu/is-status 200)
          (ltu/is-count zero?)))

    ;; invalid module subtype
    (-> session-admin
        (request base-uri
                 :request-method :post
                 :body (json/write-str (assoc valid-entry :subtype "bad-module-subtype")))
        (ltu/body->edn)
        (ltu/is-status 400))

    (when (utils/is-application? valid-entry)
      (testing "application should have compatibility attribute set"
        (-> session-user
            (request base-uri
                     :request-method :post
                     :body (json/write-str (dissoc valid-entry :compatibility)))
            (ltu/body->edn)
            (ltu/is-status 400)
            (ltu/message-matches "Application subtype should have compatibility attribute set!"))))

    ;; adding, retrieving and  deleting entry as user should succeed
    (doseq [session [session-admin session-user]]
      (let [uri     (-> session
                        (request base-uri
                                 :request-method :post
                                 :body (json/write-str valid-entry))
                        (ltu/body->edn)
                        (ltu/is-status 201)
                        (ltu/location))

            abs-uri (str p/service-context uri)]

        ;; retrieve: NOK for anon
        (-> session-anon
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 403))

        (let [content (-> session-admin
                          (request abs-uri)
                          (ltu/body->edn)
                          (ltu/is-status 200)
                          (ltu/is-key-value :compatibility "docker-compose")
                          (as-> m (if (utils/is-application? valid-entry)
                                    (ltu/is-operation-present m :validate-docker-compose)
                                    (ltu/is-operation-absent m :validate-docker-compose)))
                          (ltu/body)
                          :content)]
          (is (= valid-content (select-keys content (keys valid-content)))))

        ;; edit: NOK for anon
        (-> session-anon
            (request abs-uri
                     :request-method :put
                     :body (json/write-str valid-entry))
            (ltu/body->edn)
            (ltu/is-status 403))

        ;; insert 5 more versions
        (doseq [_ (range 5)]
          (-> session-admin
              (request abs-uri
                       :request-method :put
                       :body (json/write-str valid-entry))
              (ltu/body->edn)
              (ltu/is-status 200)))

        (let [versions (-> session-admin
                           (request abs-uri
                                    :request-method :put
                                    :body (json/write-str valid-entry))
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/body)
                           :versions)]
          (is (= 7 (count versions)))

          ;; extract by indexes or last
          (doseq [[i n] [["_0" 0] ["_1" 1] ["" 6]]]
            (let [content-id (-> session-admin
                                 (request (str abs-uri i))
                                 (ltu/body->edn)
                                 (ltu/is-status 200)
                                 (ltu/body)
                                 :content
                                 :id)]
              (is (= (-> versions (nth n) :href) content-id))
              (is (= (-> versions (nth n) :author) "someone"))
              (is (= (-> versions (nth n) :commit) "wip")))))

        ;; publish
        (let [publish-url (-> session
                              (request abs-uri)
                              (ltu/body->edn)
                              (ltu/is-status 200)
                              (ltu/is-operation-present :publish)
                              (ltu/is-operation-present :unpublish)
                              (ltu/get-op-url :publish))]

          (testing "publish last version"
            (-> session
                (request publish-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/message-matches "published successfully")))

          (testing "operation urls of specific version"
            (let [abs-uri-v2         (str abs-uri "_2")
                  resp               (-> session
                                         (request (str abs-uri "_2"))
                                         (ltu/body->edn)
                                         (ltu/is-status 200))
                  publish-url        (ltu/get-op-url resp :publish)
                  unpublish-url      (ltu/get-op-url resp :unpublish)
                  edit-url           (ltu/get-op-url resp :edit)
                  delete-url         (ltu/get-op-url resp :delete)
                  delete-version-url (ltu/get-op-url resp :delete-version)]
              (is (= publish-url (str abs-uri-v2 "/publish")))
              (is (= unpublish-url (str abs-uri-v2 "/unpublish")))
              (is (= delete-version-url (str abs-uri-v2 "/delete-version")))
              (is (= delete-url abs-uri))
              (is (= edit-url abs-uri))))

          (testing "publish specific version"
            (-> session
                (request (str abs-uri "_2/publish"))
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/message-matches "published successfully")))

          (let [unpublish-url (-> session
                                  (request abs-uri)
                                  (ltu/body->edn)
                                  (ltu/is-status 200)
                                  (ltu/is-operation-present :publish)
                                  (ltu/is-operation-present :unpublish)
                                  (ltu/is-key-value #(-> % last :published) :versions true)
                                  (ltu/is-key-value #(-> % (nth 2) :published) :versions true)
                                  (ltu/is-key-value :published true)
                                  (ltu/get-op-url :unpublish))]

            (-> session
                (request unpublish-url)
                (ltu/body->edn)
                (ltu/is-status 200)
                (ltu/message-matches "unpublished successfully")))

          ; publish is idempotent
          (-> session
              (request (str abs-uri "_2/publish"))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/message-matches "published successfully"))

          (-> session
              (request abs-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-present :publish)
              (ltu/is-operation-present :unpublish)
              (ltu/is-key-value #(-> % last :published) :versions false)
              (ltu/is-key-value :published true)
              (ltu/get-op-url :unpublish))

          (-> session
              (request (str abs-uri "_2/unpublish"))
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/message-matches "unpublished successfully"))

          (-> session
              (request abs-uri)
              (ltu/body->edn)
              (ltu/is-status 200)
              (ltu/is-operation-present :publish)
              (ltu/is-operation-present :unpublish)
              (ltu/is-key-value #(-> % (nth 2) :published) :versions false)
              (ltu/is-key-value :published false)
              (ltu/get-op-url :unpublish)))

        (testing "edit module without putting the module-content should not create new version"
          (is (= 7 (-> session-admin
                       (request abs-uri
                                :request-method :put
                                :body (json/write-str (dissoc valid-entry :content :path)))
                       (ltu/body->edn)
                       (ltu/is-status 200)
                       (ltu/body)
                       :versions
                       count))))

        (doseq [i ["_0/delete-version" "_1/delete-version"]]
          (-> session-admin
              (request (str abs-uri i))
              (ltu/body->edn)
              (ltu/is-status 200))

          (-> session-admin
              (request (str abs-uri i))
              (ltu/body->edn)
              (ltu/is-status 404)))


        (testing "delete latest version without specifying version"
          (-> session-admin
              (request (str abs-uri "/delete-version"))
              (ltu/body->edn)
              (ltu/is-status 200)))

        (testing "delete out of bound index should return 404"
          (-> session-admin
              (request (str abs-uri "_50/delete-version"))
              (ltu/body->edn)
              (ltu/is-status 404)))

        (-> session-admin
            (request (str abs-uri "_50"))
            (ltu/body->edn)
            (ltu/is-status 404))

        ;; delete: NOK for anon
        (-> session-anon
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 403))

        (-> session-admin
            (request abs-uri
                     :request-method :delete)
            (ltu/body->edn)
            (ltu/is-status 200))

        ;; verify that the resource was deleted.
        (-> session-admin
            (request abs-uri)
            (ltu/body->edn)
            (ltu/is-status 404))))))

(deftest lifecycle-component
  (let [valid-component {:author        "someone"
                         :commit        "wip"

                         :architectures ["amd64" "arm/v6"]
                         :image         {:image-name "ubuntu"
                                         :tag        "16.04"}
                         :ports         [{:protocol       "tcp"
                                          :target-port    22
                                          :published-port 8022}]}]
    (lifecycle-test-module utils/subtype-comp valid-component)))


(deftest lifecycle-application
  (let [valid-application {:author         "someone"
                           :commit         "wip"
                           :docker-compose "version: \"3.6\"\n\nx-common: &common\n  stop_grace_period: 4s\n  logging:\n    options:\n      max-size: \"250k\"\n      max-file: \"10\"\n  labels:\n    - \"nuvlabox.component=True\"\n    - \"nuvlabox.deployment=production\"\n\nvolumes:\n  nuvlabox-db:\n    driver: local\n\nnetworks:\n  nuvlabox-shared-network:\n    driver: overlay\n    name: nuvlabox-shared-network\n    attachable: true\n\nservices:\n  data-gateway:\n    <<: *common\n    image: traefik:2.1.1\n    container_name: datagateway\n    restart: on-failure\n    command:\n      - --entrypoints.mqtt.address=:1883\n      - --entrypoints.web.address=:80\n      - --providers.docker=true\n      - --providers.docker.exposedbydefault=false\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n    networks:\n      - default\n      - nuvlabox-shared-network\n\n  nb-mosquitto:\n    <<: *common\n    image: eclipse-mosquitto:1.6.8\n    container_name: nbmosquitto\n    restart: on-failure\n    labels:\n      - \"traefik.enable=true\"\n      - \"traefik.tcp.routers.mytcprouter.rule=HostSNI(`*`)\"\n      - \"traefik.tcp.routers.mytcprouter.entrypoints=mqtt\"\n      - \"traefik.tcp.routers.mytcprouter.service=mosquitto\"\n      - \"traefik.tcp.services.mosquitto.loadbalancer.server.port=1883\"\n      - \"nuvlabox.component=True\"\n      - \"nuvlabox.deployment=production\"\n    healthcheck:\n      test: [\"CMD-SHELL\", \"timeout -t 5 mosquitto_sub -t '$$SYS/#' -C 1 | grep -v Error || exit 1\"]\n      interval: 10s\n      timeout: 10s\n      start_period: 10s\n\n  system-manager:\n    <<: *common\n    image: nuvlabox/system-manager:1.0.1\n    restart: always\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n      - nuvlabox-db:/srv/nuvlabox/shared\n    ports:\n      - 127.0.0.1:3636:3636\n    healthcheck:\n      test: [\"CMD\", \"curl\", \"-f\", \"http://localhost:3636\"]\n      interval: 30s\n      timeout: 10s\n      retries: 4\n      start_period: 10s\n\n  agent:\n    <<: *common\n    image: nuvlabox/agent:1.3.2\n    restart: on-failure\n    environment:\n      - NUVLABOX_UUID=${NUVLABOX_UUID}\n      - NUVLA_ENDPOINT=${NUVLA_ENDPOINT:-nuvla.io}\n      - NUVLA_ENDPOINT_INSECURE=${NUVLA_ENDPOINT_INSECURE:-False}\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n      - nuvlabox-db:/srv/nuvlabox/shared\n      - /:/rootfs:ro\n    expose:\n      - 5000\n    depends_on:\n      - system-manager\n      - compute-api\n\n  management-api:\n    <<: *common\n    image: nuvlabox/management-api:0.1.0\n    restart: on-failure\n    environment:\n      - NUVLA_ENDPOINT=${NUVLA_ENDPOINT:-nuvla.io}\n      - NUVLA_ENDPOINT_INSECURE=${NUVLA_ENDPOINT_INSECURE:-False}\n    volumes:\n      - /proc/sysrq-trigger:/sysrq\n      - ${HOME}/.ssh/authorized_keys:/rootfs/.ssh/authorized_keys\n      - nuvlabox-db:/srv/nuvlabox/shared\n      - /var/run/docker.sock:/var/run/docker.sock\n    ports:\n      - 5001:5001\n    healthcheck:\n      test: curl -k https://localhost:5001 2>&1 | grep SSL\n      interval: 20s\n      timeout: 10s\n      start_period: 30s\n\n  compute-api:\n    <<: *common\n    image: nuvlabox/compute-api:0.2.5\n    restart: on-failure\n    pid: \"host\"\n    environment:\n      - HOST=${HOSTNAME:-nuvlabox}\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n      - nuvlabox-db:/srv/nuvlabox/shared\n    ports:\n      - 5000:5000\n    depends_on:\n      - system-manager\n\n  network-manager:\n    <<: *common\n    image: nuvlabox/network-manager:0.0.4\n    restart: on-failure\n    environment:\n      - NUVLABOX_UUID=${NUVLABOX_UUID}\n      - VPN_INTERFACE_NAME=${NUVLABOX_VPN_IFACE:-vpn}\n    volumes:\n      - nuvlabox-db:/srv/nuvlabox/shared\n    depends_on:\n      - system-manager\n\n  vpn-client:\n    <<: *common\n    image: nuvlabox/vpn-client:0.0.4\n    container_name: vpn-client\n    restart: always\n    network_mode: host\n    cap_add:\n      - NET_ADMIN\n    devices:\n      - /dev/net/tun\n    environment:\n      - NUVLABOX_UUID=${NUVLABOX_UUID}\n    volumes:\n      - nuvlabox-db:/srv/nuvlabox/shared\n    depends_on:\n      - network-manager"}]

    (lifecycle-test-module utils/subtype-app valid-application)))


(def valid-applications-sets-content
  {:author            "someone"
   :commit            "wip"
   :applications-sets [{:name         "x"
                        :applications [{:id      "module/x"
                                        :version 0}]}]})

(deftest lifecycle-applications-sets
  (lifecycle-test-module utils/subtype-apps-sets valid-applications-sets-content))


(deftest lifecycle-applications-sets-extended
  (let [session-anon      (-> (session (ltu/ring-app))
                              (content-type "application/json"))
        session-user      (header session-anon authn-info-header
                                  "user/jane user/jane group/nuvla-user group/nuvla-anon")

        valid-app-1       {:parent-path   "a/b"
                           :path          "clara/app-1"
                           :subtype       utils/subtype-app
                           :compatibility "docker-compose"
                           :content       {:author         "someone"
                                           :commit         "initial"
                                           :docker-compose "some content"}}
        app-1-create-resp (-> session-user
                              (request base-uri
                                       :request-method :post
                                       :body (json/write-str valid-app-1))
                              (ltu/body->edn)
                              (ltu/is-status 201))
        app-1-uri         (ltu/location-url app-1-create-resp)
        app-1-id          (ltu/location app-1-create-resp)]

    (let [valid-entry {:parent-path "a/b"
                       :path        "a/b/c"
                       :subtype     utils/subtype-apps-sets
                       :content     (assoc-in valid-applications-sets-content
                                              [:applications-sets 0
                                               :applications 0 :id] app-1-id)}]

      (-> session-user
          (request app-1-uri
                   :request-method :put
                   :body (json/write-str
                           (update valid-app-1 :content assoc
                                   :docker-compose "content changed"
                                   :commit "second commit")))
          (ltu/body->edn)
          (ltu/is-status 200))

      (let [response   (-> session-user
                           (request base-uri
                                    :request-method :post
                                    :body (json/write-str valid-entry))
                           (ltu/body->edn)
                           (ltu/is-status 201))
            uri        (ltu/location response)
            abs-uri    (ltu/location-url response)
            deploy-uri (-> session-user
                           (request abs-uri)
                           (ltu/body->edn)
                           (ltu/is-status 200)
                           (ltu/get-op-url :deploy))]
        (-> session-user
            (request deploy-uri)
            (ltu/body->edn)
            (ltu/is-status 200)
            (ltu/is-key-value :application uri)
            (ltu/is-key-value :version 0)
            (ltu/is-key-value #(-> %
                                   first
                                   :applications
                                   first
                                   :resolved
                                   :content
                                   :docker-compose)
                              :applications-sets
                              "some content"))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id module/resource-type))]
    (ltu/verify-405-status [[base-uri :delete]
                            [resource-uri :post]])))
