(ns com.sixsq.nuvla.server.resources.mec.nuvla-backed-mepm-test
  (:require
    [clj-http.client :as http]
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.mec.nuvla-backed-mepm :as nuvla-backed-mepm]
    [environ.core :as env]
    [jsonista.core :as json]))


(defn- body->map
  [request]
  (some-> (:body request)
          (json/read-value json/keyword-keys-object-mapper)))


(defn- configured-env
  [k & [default]]
  (case k
    :nuvla-endpoint "http://localhost:8200"
    :nuvla-api-key "credential/mock-key"
    :nuvla-api-secret "mock-secret"
    default))


(deftest test-create-backing-deployment
  (testing "Create backing deployment persists explicit MEC correlation on both sides over HTTP"
    (let [requests (atom [])]
      (with-redefs [env/env configured-env
                    http/request (fn [request]
                                   (swap! requests conj request)
                                   (cond
                                     (= [(:method request) (:url request)]
                                        [:post "http://localhost:8200/api/session"])
                                     {:status 201
                                      :headers {"set-cookie" "com.com.sixsq.nuvla.cookie=mock-token; Secure; Path=/; HttpOnly"}
                                      :body "{\"resource-id\":\"session/mock\"}"}

                                     (= [(:method request) (:url request)]
                                        [:get "http://localhost:8200/api/deployment/mec-1"])
                                     {:status 200
                                      :body   "{\"id\":\"deployment/mec-1\",\"name\":\"MEC demo deployment\",\"module\":{\"href\":\"module/test-app\"},\"nuvlabox\":\"nuvlabox/edge-1\",\"acl\":{\"owners\":[\"group/nuvla-admin\"]},\"tags\":[\"MEC\"]}"}

                                     (= [(:method request) (:url request)]
                                        [:get "http://localhost:8200/api/module/test-app"])
                                     {:status 200
                                      :body   "{\"id\":\"module/test-app\",\"name\":\"Demo MEC App\",\"content\":{\"appName\":\"Demo MEC App\",\"appProvider\":\"Acme\",\"swImageDescriptor\":[{\"swImageName\":\"demo-app\",\"swImageVersion\":\"1.0\",\"containerFormat\":\"DOCKER\",\"swImage\":\"registry.example.com/demo:1.0\"}]}}"}

                                     (= [(:method request) (:url request) (:query-params request)]
                                        [:get "http://localhost:8200/api/module" {"filter" "path='mec-native'" "last" "100"}])
                                     {:status 200
                                      :body   "{\"resources\":[]}"}

                                     (= [(:method request) (:url request)]
                                        [:post "http://localhost:8200/api/module"])
                                     (let [body (body->map request)]
                                       (cond
                                         (= {:path "mec-native"
                                             :name "MEC Native"
                                             :description "Auto-generated native backing applications for ETSI MEC modules."
                                             :subtype "project"}
                                            body)
                                         {:status 201
                                          :body   "{\"resource-id\":\"module/project-1\"}"}

                                         (= "mec-native/test-app" (:path body))
                                         {:status 201
                                          :body   "{\"resource-id\":\"module/native-1\"}"}

                                         :else
                                         (throw (ex-info "Unexpected module creation body" {:request request :body body}))))

                                     (= [(:method request) (:url request) (:query-params request)]
                                        [:get "http://localhost:8200/api/module" {"filter" "path='mec-native/test-app'" "last" "100"}])
                                     {:status 200
                                      :body   "{\"resources\":[]}"}

                                     (= [(:method request) (:url request)]
                                        [:get "http://localhost:8200/api/module/native-1"])
                                     {:status 200
                                      :body   "{\"id\":\"module/native-1\",\"path\":\"mec-native/test-app\",\"content\":{\"docker-compose\":\"version: '3.9'\\nservices:\\n  demo-app-1:\\n    image: registry.example.com/demo:1.0\\n    restart: unless-stopped\\n\"}}"}

                                     (= [(:method request) (:url request)]
                                        [:get "http://localhost:8200/api/nuvlabox/edge-1"])
                                     {:status 200
                                      :body   "{\"id\":\"nuvlabox/edge-1\",\"infrastructure-service-group\":\"infrastructure-service-group/isg-1\"}"}

                                     (= [(:method request) (:url request) (:query-params request)]
                                        [:get "http://localhost:8200/api/infrastructure-service" {"filter" "subtype='swarm' and parent='infrastructure-service-group/isg-1'" "last" "100" "select" "id,parent"}])
                                     {:status 200
                                      :body   "{\"resources\":[{\"id\":\"infrastructure-service/service-1\",\"parent\":\"infrastructure-service-group/isg-1\"}]}"}

                                     (= [(:method request) (:url request) (:query-params request)]
                                        [:get "http://localhost:8200/api/credential" {"filter" "subtype='infrastructure-service-swarm' and parent='infrastructure-service/service-1'" "last" "100" "select" "id,parent,subtype"}])
                                     {:status 200
                                      :body   "{\"resources\":[{\"id\":\"credential/service-cred-1\",\"parent\":\"infrastructure-service/service-1\",\"subtype\":\"infrastructure-service-swarm\"}]}"}

                                     (= [(:method request) (:url request)]
                                        [:post "http://localhost:8200/api/deployment"])
                                     {:status 201
                                      :body   "{\"resource-id\":\"deployment/backing-1\"}"}

                                     (= [(:method request) (:url request)]
                                        [:put "http://localhost:8200/api/deployment/mec-1"])
                                     {:status 200 :body "{\"id\":\"deployment/mec-1\"}"}

                                     (= [(:method request) (:url request)]
                                        [:put "http://localhost:8200/api/deployment/backing-1"])
                                     {:status 200 :body "{\"id\":\"deployment/backing-1\"}"}

                                     (= [(:method request) (:url request)]
                                        [:post "http://localhost:8200/api/deployment/backing-1/start"])
                                     {:status 202
                                      :body   "{\"location\":\"job/backing-start-1\"}"}

                                     (= [(:method request) (:url request)]
                                        [:get "http://localhost:8200/api/deployment/backing-1"])
                                     {:status 200
                                      :body   "{\"id\":\"deployment/backing-1\",\"module\":{\"href\":\"module/native-1\"},\"parent\":\"credential/service-cred-1\",\"state\":\"STARTING\"}"}

                                     :else
                                     (throw (ex-info "Unexpected HTTP request" {:request request}))))]
        (let [result (nuvla-backed-mepm/create-backing-deployment!
                       "mepm/test-1"
                       {:appInstanceId "deployment/mec-1"
                        :mecHostId     "nuvlabox/edge-1"})]
          (is (= {:template {:href "session-template/api-key"
                             :key "credential/mock-key"
                             :secret "mock-secret"}}
                 (body->map (first @requests))))
          (is (= "com.com.sixsq.nuvla.cookie=mock-token"
                 (get-in (second @requests) [:headers "cookie"])))
          (is (= "mec-native/test-app"
                 (:path (body->map (nth @requests 6)))))
          (is (re-find #"registry\.example\.com/demo:1\.0"
                       (get-in (body->map (nth @requests 6)) [:content :docker-compose])))
          (is (= {:module {:href "module/native-1"}
                  :acl    {:owners ["group/nuvla-admin"]}
                  :parent "credential/service-cred-1"
                  :name   "MEC demo deployment"}
                 (body->map (nth @requests 11))))
          (is (= {:mec-backing-deployment-id "deployment/backing-1"}
                 (body->map (nth @requests 12))))
          (is (= {:mec-app-instance-id       "deployment/mec-1"
                  :mec-backing-deployment-id "deployment/backing-1"
                  :mec-mepm-id               "mepm/test-1"
                  :mec-host-id               "nuvlabox/edge-1"
                  :mec-backend-mode          "NUVLA_BACKED"}
                 (body->map (nth @requests 13))))
          (is (= nil (:body (nth @requests 14))))
          (is (= "deployment/backing-1" (:mec-backing-deployment-id result)))
          (is (= "deployment/mec-1" (:mec-app-instance-id result)))
          (is (= "mepm/test-1" (:mec-mepm-id result)))
          (is (= "module/native-1" (:native-module-id result)))
          (is (= "credential/service-cred-1" (:native-parent-id result)))
          (is (= {:location "job/backing-start-1"} (:start-response result)))
          (is (= "NUVLA_BACKED" (:mec-backend-mode result))))))))


(deftest test-operate-backing-deployment
  (testing "Operate backing deployment resolves from MEC deployment correlation over HTTP"
    (let [requests (atom [])]
      (with-redefs [env/env configured-env
                    http/request (fn [request]
                                   (swap! requests conj request)
                                   (case [(:method request) (:url request)]
                                     [:post "http://localhost:8200/api/session"]
                                     {:status 201 :body "{\"resource-id\":\"session/mock\"}"}

                                     [:get "http://localhost:8200/api/deployment/mec-1"]
                                     {:status 200
                                      :body   "{\"id\":\"deployment/mec-1\",\"mec-backing-deployment-id\":\"deployment/backing-1\"}"}

                                     [:get "http://localhost:8200/api/deployment/backing-1"]
                                     {:status 200
                                      :body   "{\"id\":\"deployment/backing-1\",\"mec-app-instance-id\":\"deployment/mec-1\",\"state\":\"STOPPED\"}"}

                                     [:post "http://localhost:8200/api/deployment/backing-1/start"]
                                     {:status 202
                                      :body   "{\"location\":\"job/backing-start-1\"}"}

                                     (throw (ex-info "Unexpected HTTP request" {:request request}))))]
        (let [result (nuvla-backed-mepm/operate-backing-deployment!
                       "mepm/test-1"
                       {:appInstanceId "deployment/mec-1"
                        :changeStateTo "STARTED"})]
          (is (= nil (:body (nth @requests 3))))
          (is (= "deployment/mec-1" (:mec-app-instance-id result)))
          (is (= "deployment/backing-1" (:backing-deployment-id result)))
          (is (= "STARTED" (:change-state-to result))))))))


(deftest test-terminate-backing-deployment
  (testing "Terminate deletes a stopped backing deployment over HTTP"
    (let [requests (atom [])]
      (with-redefs [env/env configured-env
                    http/request (fn [request]
                                   (swap! requests conj request)
                                   (case [(:method request) (:url request)]
                                     [:post "http://localhost:8200/api/session"]
                                     {:status 201 :body "{\"resource-id\":\"session/mock\"}"}

                                     [:get "http://localhost:8200/api/deployment/backing-1"]
                                     {:status 200
                                      :body   "{\"id\":\"deployment/backing-1\",\"mec-app-instance-id\":\"deployment/mec-1\",\"state\":\"STOPPED\"}"}

                                     [:delete "http://localhost:8200/api/deployment/backing-1"]
                                     {:status 200
                                      :body   "{\"resource-id\":\"deployment/backing-1\"}"}

                                     (throw (ex-info "Unexpected HTTP request" {:request request}))))]
        (let [result (nuvla-backed-mepm/terminate-backing-deployment!
                       "mepm/test-1"
                       {:appInstanceId "deployment/backing-1"})]
          (is (= :delete (:method (nth @requests 2))))
          (is (= "delete" (:action result)))
          (is (= "deployment/backing-1" (:backing-deployment-id result)))))))

  (testing "Terminate stops a running backing deployment when delete is not yet allowed"
    (let [requests (atom [])]
      (with-redefs [env/env configured-env
                    http/request (fn [request]
                                   (swap! requests conj request)
                                   (case [(:method request) (:url request)]
                                     [:post "http://localhost:8200/api/session"]
                                     {:status 201 :body "{\"resource-id\":\"session/mock\"}"}

                                     [:get "http://localhost:8200/api/deployment/backing-1"]
                                     {:status 200
                                      :body   "{\"id\":\"deployment/backing-1\",\"mec-app-instance-id\":\"deployment/mec-1\",\"state\":\"STARTED\"}"}

                                     [:post "http://localhost:8200/api/deployment/backing-1/stop"]
                                     {:status 202
                                      :body   "{\"location\":\"job/backing-stop-1\"}"}

                                     (throw (ex-info "Unexpected HTTP request" {:request request}))))]
        (let [result (nuvla-backed-mepm/terminate-backing-deployment!
                       "mepm/test-1"
                       {:appInstanceId "deployment/backing-1"})]
          (is (= :post (:method (nth @requests 2))))
          (is (= "stop" (:action result)))
          (is (= "deployment/backing-1" (:backing-deployment-id result))))))))
