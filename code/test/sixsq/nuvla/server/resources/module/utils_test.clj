(ns sixsq.nuvla.server.resources.module.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.module.utils :as t]))


(deftest split-resource
  (is (= [{:alpha 1, :beta 2} {:gamma 3}]
         (t/split-resource {:alpha   1
                            :beta    2
                            :content {:gamma 3}}))))


(deftest check-parent-path
  (are [parent-path path] (= parent-path (t/get-parent-path path))
                          nil nil
                          "" "alpha"
                          "alpha" "alpha/beta"
                          "alpha/beta" "alpha/beta/gamma"))


(deftest check-set-parent-path
  (are [expected arg] (= expected (:parent-path (t/set-parent-path arg)))
                      "ok" {:parent-path "bad-value"
                            :path        "ok/go"}
                      nil {}))

(def docker-compose-0-str "version: '3'\n\nservices:\n web:\n  image: nginx\n")
(def docker-compose-1-str "version: \"3.7\"\n\nservices:\n  nginx_simple:\n    image: nginx:latest\n    ports:\n      - 80\n    configs:\n      - source: index\n        target: /usr/share/nginx/html/index.html\n      - my_config\n    environment:\n      - ENV_1=\"Hello World\"\n      # This is a variable is taken from deployment environmental variables. They can be edited at runtime\n      - MY_ENV=$MY_ENV\n      - MY_ENV_COPY=$MY_ENV\n      - GIVE_ME_SOME_INPUT=$GIVE_ME_SOME_INPUT\n      # NUVLA_... generated variables allow a service to edit the deployment and deployment parameters.\n      # If you need them just add them to the service \"environment\" to make them available to it. \n      - NUVLA_DEPLOYMENT_ID=$NUVLA_DEPLOYMENT_ID\n      - NUVLA_ENDPOINT=$NUVLA_ENDPOINT\n      - NUVLA_API_KEY=$NUVLA_API_KEY\n      - NUVLA_API_SECRET='***** no will not make this available for everyone :P'\n    command: bash -c \"\n      env > /usr/share/nginx/html/env.txt;\n      cat /my_config > /usr/share/nginx/html/my_config.txt;\n      nginx -g 'daemon off;'\n      \"\n\n  jupyter_advanced:\n    image: jupyter/minimal-notebook:latest\n    ports:\n      - 8888\n    environment:\n      - NUVLA_DEPLOYMENT_ID=$NUVLA_DEPLOYMENT_ID\n      - NUVLA_ENDPOINT=$NUVLA_ENDPOINT\n      - NUVLA_API_KEY=$NUVLA_API_KEY\n      - NUVLA_API_SECRET=$NUVLA_API_SECRET\n    configs:\n      - source: Nuvla-app-demo\n        target: /home/jovyan/Nuvla-app-demo.ipynb\n    secrets:\n      - jupyter_token\n    command: bash -c \"/usr/local/bin/start-notebook.sh --NotebookApp.token=$$(cat /run/secrets/jupyter_token)\"\n\nsecrets:\n  jupyter_token:\n    file: ./jupyter_token.txt\n\nconfigs:\n  index:\n    file: ./index.html\n  my_config:\n    file: ./my_config.txt\n  Nuvla-app-demo:\n    file: ./Nuvla-app-demo.ipynb\n")
(def docker-compose-2-str "##########################################################################\n##                         AUTO-GENERATED FILE                          ##\n##########################################################################\n\nversion: '2'\nnetworks:\n    cluster:\n        driver: bridge\n\nvolumes:\n    pgmaster:\n    pgslave1:\n    pgslave2:\n    pgslave3:\n    pgslave4:\n    backup:\n\nservices:\n    pgmaster:\n        build:\n            context: ../src\n            dockerfile: Postgres-11-Repmgr-4.0.Dockerfile\n        environment:\n            NODE_ID: 1 # Integer number of node (not required if can be extracted from NODE_NAME var, e.g. node-45 => 1045)\n            NODE_NAME: node1 # Node name\n            CLUSTER_NODE_NETWORK_NAME: pgmaster # (default: hostname of the node)\n            \n            PARTNER_NODES: \"pgmaster,pgslave1,pgslave3\"\n            REPLICATION_PRIMARY_HOST: pgmaster # That should be ignored on the same node\n            \n            NODE_PRIORITY: 100  # (default: 100)\n            SSH_ENABLE: 1\n            #database we want to use for application\n            POSTGRES_PASSWORD: monkey_pass\n            POSTGRES_USER: monkey_user\n            POSTGRES_DB: monkey_db\n            CLEAN_OVER_REWIND: 0\n            CONFIGS_DELIMITER_SYMBOL: ;\n            CONFIGS: \"listen_addresses:'*';max_replication_slots:5\"\n                                  # in format variable1:value1[,variable2:value2[,...]] if CONFIGS_DELIMITER_SYMBOL=, and CONFIGS_ASSIGNMENT_SYMBOL=:\n                                  # used for pgpool.conf file\n            #defaults:\n            CLUSTER_NAME: pg_cluster # default is pg_cluster\n            REPLICATION_DB: replication_db # default is replication_db\n            REPLICATION_USER: replication_user # default is replication_user\n            REPLICATION_PASSWORD: replication_pass # default is replication_pass\n            \n        ports:\n            - 5422:5432\n        volumes:\n            - pgmaster:/var/lib/postgresql/data\n            - ./ssh/:/tmp/.ssh/keys\n        networks:\n            cluster:\n                aliases:\n                    - pgmaster\n#<<< Branch 1\n    pgslave1:\n        build:\n            context: ../src\n            dockerfile: Postgres-11-Repmgr-4.0.Dockerfile\n        environment:\n            NODE_ID: 2\n            NODE_NAME: node2\n            CLUSTER_NODE_NETWORK_NAME: pgslave1 # (default: hostname of the node)\n            SSH_ENABLE: 1\n            PARTNER_NODES: \"pgmaster,pgslave1,pgslave3\"\n            REPLICATION_PRIMARY_HOST: pgmaster\n            CLEAN_OVER_REWIND: 1\n            CONFIGS_DELIMITER_SYMBOL: ;\n            CONFIGS: \"max_replication_slots:10\" #some overrides\n        ports:\n            - 5441:5432\n        volumes:\n            - pgslave1:/var/lib/postgresql/data\n            - ./ssh:/tmp/.ssh/keys\n        networks:\n            cluster:\n                aliases:\n                    - pgslave1\n\n    # Add more slaves if required\n    pgslave2:\n        build:\n            context: ../src\n            dockerfile: Postgres-11-Repmgr-4.0.Dockerfile\n        environment:\n            NODE_ID: 3\n            NODE_NAME: node3\n            CLUSTER_NODE_NETWORK_NAME: pgslave2 # (default: hostname of the node)\n\n            REPLICATION_PRIMARY_HOST: pgslave1 # I want to have cascade Streeming replication\n            #USE_REPLICATION_SLOTS: 0\n            CONFIGS_DELIMITER_SYMBOL: ;\n            CONFIGS: \"listen_addresses:'*'\"\n        ports:\n            - 5442:5432\n        volumes:\n            - pgslave2:/var/lib/postgresql/data\n        networks:\n            cluster:\n                aliases:\n                    - pgslave2\n#>>> Branch 1\n#<<< Branch 2\n    pgslave3:\n        build:\n            context: ../src\n            dockerfile: Postgres-11-Repmgr-4.0.Dockerfile\n        environment:\n            NODE_ID: 4\n            NODE_NAME: node4\n            CLUSTER_NODE_NETWORK_NAME: pgslave3 # (default: hostname of the node)\n            SSH_ENABLE: 1\n            PARTNER_NODES: \"pgmaster,pgslave1,pgslave3\"\n            REPLICATION_PRIMARY_HOST: pgmaster\n            NODE_PRIORITY: 200  # (default: 100)\n            CLEAN_OVER_REWIND: 1\n            CONFIGS_DELIMITER_SYMBOL: ;\n            CONFIGS: \"listen_addresses:'*'\"\n        ports:\n            - 5443:5432\n        volumes:\n            - pgslave3:/var/lib/postgresql/data\n            - ./ssh:/tmp/.ssh/keys\n        networks:\n            cluster:\n                aliases:\n                    - pgslave3\n\n    pgslave4:\n        build:\n            context: ../src\n            dockerfile: Postgres-11-Repmgr-4.0.Dockerfile\n        environment:\n            NODE_ID: 5\n            NODE_NAME: node5\n            CLUSTER_NODE_NETWORK_NAME: pgslave4 # (default: hostname of the node)\n\n            REPLICATION_PRIMARY_HOST: pgslave3\n            #USE_REPLICATION_SLOTS: 0\n            CONFIGS_DELIMITER_SYMBOL: ;\n            CONFIGS: \"listen_addresses:'*'\"\n        ports:\n            - 5444:5432\n        volumes:\n            - pgslave4:/var/lib/postgresql/data\n        networks:\n            cluster:\n                aliases:\n                    - pgslave4\n#>>> Branch 2\n    backup:\n        build:\n            context: ../src\n            dockerfile: Barman-2.4-Postgres-11.Dockerfile\n        environment:\n            REPLICATION_USER: replication_user # default is replication_user\n            REPLICATION_PASSWORD: replication_pass # default is replication_pass\n            REPLICATION_HOST: pgmaster\n            POSTGRES_PASSWORD: monkey_pass\n            POSTGRES_USER: monkey_user\n            POSTGRES_DB: monkey_db\n            SSH_ENABLE: 1\n            BACKUP_SCHEDULE: \"*/30 */5 * * *\"\n        volumes:\n            - backup:/var/backups\n            - ./ssh:/tmp/.ssh/keys\n        networks:\n            cluster:\n                aliases:\n                    - backup\n    pgpool:\n        build:\n            context: ../src\n            dockerfile: Pgpool-3.7-Postgres-11.Dockerfile\n        environment:\n            PCP_USER: pcp_user\n            PCP_PASSWORD: pcp_pass\n            WAIT_BACKEND_TIMEOUT: 60\n\n            CHECK_USER: monkey_user\n            CHECK_PASSWORD: monkey_pass\n            CHECK_PGCONNECT_TIMEOUT: 3 #timout for checking if primary node is healthy\n            SSH_ENABLE: 1\n            DB_USERS: monkey_user:monkey_pass # in format user:password[,user:password[...]]\n            BACKENDS: \"0:pgmaster:5432:1:/var/lib/postgresql/data:ALLOW_TO_FAILOVER,1:pgslave1::::,3:pgslave3::::,2:pgslave2::::\" #,4:pgslaveDOES_NOT_EXIST::::\n                      # in format num:host:port:weight:data_directory:flag[,...]\n                      # defaults:\n                      #   port: 5432\n                      #   weight: 1\n                      #   data_directory: /var/lib/postgresql/data\n                      #   flag: ALLOW_TO_FAILOVER\n            REQUIRE_MIN_BACKENDS: 3 # minimal number of backends to start pgpool (some might be unreachable)\n            CONFIGS: \"num_init_children:250,max_pool:4\"\n                      # in format variable1:value1[,variable2:value2[,...]] if CONFIGS_DELIMITER_SYMBOL=, and CONFIGS_ASSIGNMENT_SYMBOL=:\n                      # used for pgpool.conf file\n        ports:\n            - 5430:5432\n            - 9898:9898 # PCP\n        volumes:\n            - ./ssh:/tmp/.ssh/keys\n        networks:\n            cluster:\n                aliases:\n                    - pgpool\n# the rest is for tests\n    postgres_ext:\n        build:\n            context: ../src\n            dockerfile: Postgres-extended-11-Repmgr-4.0.Dockerfile\n        environment:\n          NODE_ID: 101 \n          NODE_NAME: node101 # Node name\n          CLUSTER_NODE_NETWORK_NAME: postgres_ext # (default: hostname of the node)\n          POSTGRES_PASSWORD: monkey_pass\n          POSTGRES_USER: monkey_user\n          POSTGRES_DB: monkey_db\n          CLEAN_OVER_REWIND: 0\n          CONFIGS_DELIMITER_SYMBOL: ;\n          CONFIGS: \"listen_addresses:'*';shared_preload_libraries:'pglogical'\"\n    postgres_conf:\n        build:\n          context: ../src/\n          dockerfile: Postgres-11-Repmgr-4.0.Dockerfile\n        environment:\n          NODE_NAME: node-102 # Node name\n          CLUSTER_NODE_NETWORK_NAME: postgres_conf # (default: hostname of the node)\n          POSTGRES_PASSWORD: monkey_pass\n          POSTGRES_USER: monkey_user\n          POSTGRES_DB: monkey_db\n          CLEAN_OVER_REWIND: 0\n          CONFIGS: \"listen_addresses)'some_host'|max_replication_slots)55\"\n          CONFIGS_DELIMITER_SYMBOL: \"|\"\n          CONFIGS_ASSIGNMENT_SYMBOL: \")\"\n    pgpool_conf:\n        build:\n            context: ../src\n            dockerfile: Pgpool-3.7-Postgres-11.Dockerfile\n        environment:\n            PCP_USER: pcp_user\n            PCP_PASSWORD: pcp_pass\n\n            CHECK_USER: monkey_user\n            CHECK_PASSWORD: monkey_pass\n\n            CHECK_PGCONNECT_TIMEOUT: 3 #timout for checking if primary node is healthy\n            DB_USERS: monkey_user:monkey_pass # in format user:password[,user:password[...]]\n            CONFIGS: \"num_init_children)12|max_pool)13\"\n            CONFIGS_DELIMITER_SYMBOL: \"|\"\n            CONFIGS_ASSIGNMENT_SYMBOL: \")\"\n        ports:\n            - 5440:5432\n            - 9897:9898 # PCP\n    pgmaster2:\n        build:\n            context: ../src\n            dockerfile: Postgres-11-Repmgr-4.0.Dockerfile\n        environment:\n\n            NODE_ID: 1\n            NODE_NAME: node1\n            CLUSTER_NODE_NETWORK_NAME: pgmaster2\n\n            POSTGRES_PASSWORD: monkey_pass\n            POSTGRES_USER: monkey_user\n            POSTGRES_DB: monkey_db\n            CONFIGS_DELIMITER_SYMBOL: ;\n            CONFIGS: \"listen_addresses:'*'\"\n        ports:\n            - 5439:5432\n        networks:\n            cluster:\n                aliases:\n                    - pgmaster2\n    pgpool2:\n        build:\n            context: ../src\n            dockerfile: Pgpool-3.7-Postgres-11.Dockerfile\n        depends_on:\n            - pgmaster\n            - pgmaster2\n        environment:\n            PCP_USER: pcp_user\n            PCP_PASSWORD: pcp_pass\n\n            CHECK_USER: monkey_user\n            CHECK_PASSWORD: monkey_pass\n            CHECK_PGCONNECT_TIMEOUT: 3 #timout for checking if primary node is healthy\n\n            DB_USERS: monkey_user:monkey_pass # in format user:password[,user:password[...]]\n            BACKENDS: \"0:pgmaster::::,1:pgmaster2::::\"\n        ports:\n            - 5431:5432\n            - 9899:9898 # PCP\n        networks:\n            cluster:\n                aliases:\n                    - pgpool2")
(def docker-compose-3-str "version: \"3.6\"\n\nx-common: &common\n  stop_grace_period: 4s\n  logging:\n    options:\n      max-size: \"250k\"\n      max-file: \"10\"\n  labels:\n    - \"nuvlabox.component=True\"\n    - \"nuvlabox.deployment=production\"\n\nvolumes:\n  nuvlabox-db:\n    driver: local\n\nnetworks:\n  nuvlabox-shared-network:\n    driver: overlay\n    name: nuvlabox-shared-network\n    attachable: true\n\nservices:\n  data-gateway:\n    <<: *common\n    image: traefik:2.1.1\n    container_name: datagateway\n    restart: on-failure\n    command:\n      - --entrypoints.mqtt.address=:1883\n      - --entrypoints.web.address=:80\n      - --providers.docker=true\n      - --providers.docker.exposedbydefault=false\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n    networks:\n      - default\n      - nuvlabox-shared-network\n\n  nb-mosquitto:\n    <<: *common\n    image: eclipse-mosquitto:1.6.8\n    container_name: nbmosquitto\n    restart: on-failure\n    labels:\n      - \"traefik.enable=true\"\n      - \"traefik.tcp.routers.mytcprouter.rule=HostSNI(`*`)\"\n      - \"traefik.tcp.routers.mytcprouter.entrypoints=mqtt\"\n      - \"traefik.tcp.routers.mytcprouter.service=mosquitto\"\n      - \"traefik.tcp.services.mosquitto.loadbalancer.server.port=1883\"\n      - \"nuvlabox.component=True\"\n      - \"nuvlabox.deployment=production\"\n    healthcheck:\n      test: [\"CMD-SHELL\", \"timeout -t 5 mosquitto_sub -t '$$SYS/#' -C 1 | grep -v Error || exit 1\"]\n      interval: 10s\n      timeout: 10s\n      start_period: 10s\n\n  system-manager:\n    <<: *common\n    image: nuvlabox/system-manager:1.0.1\n    restart: always\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n      - nuvlabox-db:/srv/nuvlabox/shared\n    ports:\n      - 127.0.0.1:3636:3636\n    healthcheck:\n      test: [\"CMD\", \"curl\", \"-f\", \"http://localhost:3636\"]\n      interval: 30s\n      timeout: 10s\n      retries: 4\n      start_period: 10s\n\n  agent:\n    <<: *common\n    image: nuvlabox/agent:1.3.2\n    restart: on-failure\n    environment:\n      - NUVLABOX_UUID=${NUVLABOX_UUID}\n      - NUVLA_ENDPOINT=${NUVLA_ENDPOINT:-nuvla.io}\n      - NUVLA_ENDPOINT_INSECURE=${NUVLA_ENDPOINT_INSECURE:-False}\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n      - nuvlabox-db:/srv/nuvlabox/shared\n      - /:/rootfs:ro\n    expose:\n      - 5000\n    depends_on:\n      - system-manager\n      - compute-api\n\n  management-api:\n    <<: *common\n    image: nuvlabox/management-api:0.1.0\n    restart: on-failure\n    environment:\n      - NUVLA_ENDPOINT=${NUVLA_ENDPOINT:-nuvla.io}\n      - NUVLA_ENDPOINT_INSECURE=${NUVLA_ENDPOINT_INSECURE:-False}\n    volumes:\n      - /proc/sysrq-trigger:/sysrq\n      - ${HOME}/.ssh/authorized_keys:/rootfs/.ssh/authorized_keys\n      - nuvlabox-db:/srv/nuvlabox/shared\n      - /var/run/docker.sock:/var/run/docker.sock\n    ports:\n      - 5001:5001\n    healthcheck:\n      test: curl -k https://localhost:5001 2>&1 | grep SSL\n      interval: 20s\n      timeout: 10s\n      start_period: 30s\n\n  compute-api:\n    <<: *common\n    image: nuvlabox/compute-api:0.2.5\n    restart: on-failure\n    pid: \"host\"\n    environment:\n      - HOST=${HOSTNAME:-nuvlabox}\n    volumes:\n      - /var/run/docker.sock:/var/run/docker.sock\n      - nuvlabox-db:/srv/nuvlabox/shared\n    ports:\n      - 5000:5000\n    depends_on:\n      - system-manager\n\n  network-manager:\n    <<: *common\n    image: nuvlabox/network-manager:0.0.4\n    restart: on-failure\n    environment:\n      - NUVLABOX_UUID=${NUVLABOX_UUID}\n      - VPN_INTERFACE_NAME=${NUVLABOX_VPN_IFACE:-vpn}\n    volumes:\n      - nuvlabox-db:/srv/nuvlabox/shared\n    depends_on:\n      - system-manager\n\n  vpn-client:\n    <<: *common\n    image: nuvlabox/vpn-client:0.0.4\n    container_name: vpn-client\n    restart: always\n    network_mode: host\n    cap_add:\n      - NET_ADMIN\n    devices:\n      - /dev/net/tun\n    environment:\n      - NUVLABOX_UUID=${NUVLABOX_UUID}\n    volumes:\n      - nuvlabox-db:/srv/nuvlabox/shared\n    depends_on:\n      - network-manager")
(def docker-compose-4-str "services: '3'")

(deftest swarm-compatibilty-check
  (let [docker-compose-0 (t/parse-and-throw-when-not-parsable-docker-compose
                           docker-compose-0-str)
        docker-compose-1 (t/parse-and-throw-when-not-parsable-docker-compose
                           docker-compose-1-str)
        docker-compose-2 (t/parse-and-throw-when-not-parsable-docker-compose
                           docker-compose-2-str)
        docker-compose-3 (t/parse-and-throw-when-not-parsable-docker-compose
                           docker-compose-3-str)
        docker-compose-4 (t/parse-and-throw-when-not-parsable-docker-compose
                           docker-compose-4-str)

        expected-0       #{"image"}
        expected-1       #{"configs" "command" "image"
                           "environment" "secrets" "ports"}
        expected-2       #{"volumes" "networks" "build"
                           "depends_on" "environment" "ports"}
        expected-3       #{"logging" "expose" "devices" "command" "image" "volumes" "network_mode"
                           "pid" "networks" "labels" "healthcheck" "depends_on" "container_name"
                           "restart" "environment" "stop_grace_period"
                           "cap_add" "ports"}
        expected-4       #{}]

    (are [expected arg] (= expected (t/docker-compose-services-keys-set arg))
                        expected-0 docker-compose-0
                        expected-1 docker-compose-1
                        expected-2 docker-compose-2
                        expected-3 docker-compose-3
                        expected-4 docker-compose-4)

    (are [expected arg] (= expected (t/list-swarm-unsupported-options arg))
                        #{} expected-0
                        #{} expected-1
                        #{"build" "depends_on"} expected-2
                        #{"devices" "network_mode" "depends_on"
                          "container_name" "restart" "cap_add"} expected-3
                        #{} expected-4)

    (are [expected arg] (= expected (t/some-services-has-swarm-options? arg))
                        false expected-0
                        true expected-1
                        false expected-2
                        false expected-3
                        false expected-4)))


(deftest parse-get-compatibility-fields
  (are [expected arg] (= expected (t/parse-get-compatibility-fields t/subtype-app arg))
                      ["swarm" []] docker-compose-0-str
                      ["swarm" []] docker-compose-1-str
                      ["docker-compose" []] docker-compose-2-str
                      ["docker-compose" []] docker-compose-3-str
                      ["swarm" []] docker-compose-4-str))

(deftest active-claim->account-id
  (is (thrown-with-msg?
        Exception
        #"unable to resolve vendor account-id for active-claim 'user/jane'"
        (t/active-claim->account-id "user/jane")))
  (with-redefs [crud/query-as-admin (constantly [nil [{:account-id "acct_x"}]])]
    (is (= (t/active-claim->account-id "user/jane") "acct_x"))))

(deftest resolve-vendor-email
  (let [module-meta {:price {:account-id "acct_x"}}
        email       "jane@example.com"]
    (is (= (t/resolve-vendor-email module-meta)
           module-meta))
    (with-redefs [crud/query-as-admin (constantly [nil [{:email email}]])]
      (is (= (t/resolve-vendor-email module-meta)
             (assoc-in module-meta [:price :vendor-email] email))))))

(deftest set-price-test
  (is (= (t/set-price {} "user/jane") {}))
  (with-redefs [pricing-impl/retrieve-price identity
                pricing-impl/price->map     identity
                t/active-claim->account-id  identity
                pricing-impl/create-price   identity
                pricing-impl/get-product    :product
                pricing-impl/get-id         :id]
    (is (= (t/set-price {:price {:cent-amount-daily 10
                                 :currency          "eur"}} "user/jane")
           {:price {:account-id        "user/jane"
                    :cent-amount-daily 10
                    :currency          "eur"
                    :price-id          nil
                    :product-id        nil}})))
  (with-redefs [pricing-impl/retrieve-price identity
                pricing-impl/price->map     (constantly {:product-id "prod_x"})
                t/active-claim->account-id  identity
                pricing-impl/create-price   (fn [{:strs [product]}]
                                              {:id      "price_x"
                                               :product product})
                pricing-impl/get-product    :product
                pricing-impl/get-id         :id]
    (is (= (t/set-price {:price {:price-id          "price_x"
                                 :cent-amount-daily 10
                                 :currency          "eur"}} "user/jane")
           {:price {:account-id        "user/jane"
                    :cent-amount-daily 10
                    :currency          "eur"
                    :price-id          "price_x"
                    :product-id        "prod_x"}}))))
