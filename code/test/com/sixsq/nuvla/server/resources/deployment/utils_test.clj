(ns com.sixsq.nuvla.server.resources.deployment.utils-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer [are deftest is testing]]
    [com.sixsq.nuvla.auth.acl-resource :as a]
    [com.sixsq.nuvla.pricing.payment :as payment]
    [com.sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [com.sixsq.nuvla.server.resources.deployment.utils :as t])
  (:import (clojure.lang ExceptionInfo)))


(deftest throw-when-payment-required
  (testing "stripe is disabled"
    (with-redefs [config-nuvla/*stripe-api-key* nil
                  a/is-admin?                   (constantly false)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "is admin"
    (with-redefs [config-nuvla/*stripe-api-key* "123"
                  a/is-admin?                   (constantly true)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer active and can pay"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "active"})
                  payment/can-pay?                   (constantly true)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer is trialing"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "trialing"})
                  payment/can-pay?                   (constantly false)]
      (is (= (t/throw-when-payment-required {} {}) {}))))
  (testing "customer past_due and has not payment"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "past_due"})
                  payment/can-pay?                   (constantly false)]
      (is (= {} (t/throw-when-payment-required {} {})))))
  (testing "customer active and price set but can't pay"
    (with-redefs [config-nuvla/*stripe-api-key*      "123"
                  a/is-admin?                        (constantly false)
                  payment/active-claim->s-customer   (constantly nil)
                  payment/active-claim->subscription (constantly {:status "active"})
                  payment/can-pay?                   (constantly false)]
      (is (= {} (t/throw-when-payment-required {} {})))))
  (let [billable-deployment-follow     {:module {:price {:price-id              "price_id"
                                                         :follow-customer-trial true}}}
        billable-deployment-not-follow {:module {:price {:price-id              "price_id"
                                                         :follow-customer-trial false}}}]
    (testing "customer is active"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->subscription (constantly {:status "active"})]
        (is (= billable-deployment-follow
               (t/throw-when-payment-required billable-deployment-follow {})))))
    (testing "customer is past_due"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->subscription (constantly {:status "past_due"})]
        (is (= billable-deployment-follow
               (t/throw-when-payment-required billable-deployment-follow {})))))
    (testing "customer trialing with module price set and follow trial"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly false)]
        (is (= (t/throw-when-payment-required billable-deployment-follow {})
               billable-deployment-follow))))
    (testing "customer trialing with module price set and follow trial is not set"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly false)]
        (is (thrown-with-msg? ExceptionInfo
                              #"Valid subscription and payment method are needed!"
                              (t/throw-when-payment-required billable-deployment-not-follow {})))))

    (testing "customer trialing with module price set and follow trial is not set but can pay"
      (with-redefs [config-nuvla/*stripe-api-key*      "123"
                    a/is-admin?                        (constantly false)
                    payment/active-claim->s-customer   (constantly nil)
                    payment/active-claim->subscription (constantly {:status "trialing"})
                    payment/can-pay?                   (constantly true)]
        (is (= (t/throw-when-payment-required billable-deployment-not-follow {})
               billable-deployment-not-follow))))
    (testing "vendor or user with edit-data rights"
      (with-redefs [config-nuvla/*stripe-api-key* "123"
                    a/can-edit-data?              (constantly true)]
        (is (= (t/throw-when-payment-required billable-deployment-not-follow {})
               billable-deployment-not-follow))))))

(deftest cred-edited?
  (are [expected parent current-parent]
    (= expected (t/cred-edited? parent current-parent))
    false nil nil
    false nil "credential/x"
    false "credential/x" "credential/x"
    true "credential/y" "credential/x"))

(deftest keep-defined-values
  (let [m-a {:name "a" :value "a-v" :description "a-d"}
        m-b {:name "b" :description "b-d"}]
    (are [expected arg-map]
      (= expected (t/keep-current-defined-values (:arg1 arg-map) (:arg2 arg-map)))
      [] {:arg1 [] :arg2 []}
      [] {:arg1 [m-a] :arg2 []}
      [m-a] {:arg1 [m-a] :arg2 [{:name "a" :description "a-d"}]}
      [m-a m-b] {:arg1 [m-a m-b] :arg2 [{:name "a" :description "a-d"} m-b]}
      [(assoc m-a :description "a-d-changed") m-b] {:arg1 [m-a m-b] :arg2 [{:name "a" :description "a-d-changed"} m-b]}
      [m-a m-b] {:arg1 [m-a m-b] :arg2 [m-a (assoc m-b :value "any")]})))

(deftest throw-can-not-access-helm-repo-cred
  (is (= (t/throw-can-not-access-helm-repo-cred {} {}) {})))

(def minimal-nb-status {:id            "nuvlabox-status/fb4da83b-e911-4f01-8bed-82a8473ac8e3",
                        :ip            "178.194.193.87",
                        :network       {:ips
                                        {:public "178.194.193.87",
                                         :swarm  "192.168.64.4",
                                         :vpn    "",
                                         :local  ""}}
                        :coe-resources {:docker
                                        {:containers [{:Id "f271e2566489ab4beca2ac193935076f8cf89b5ab768c78962a4ea028c9ab51c"},
                                                      {:Image  "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
                                                       :Labels {:com.docker.compose.service "agent",
                                                                :com.docker.compose.project "b3b70820-2de4-4a11-b00c-a79661c3d433"},
                                                       :Id     "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef"}]}}})

(deftest get-deployment-state
  (is (= [{:parent  "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name    "agent.image",
           :value   "nuvladev/nuvlaedge:detect-nuvla-coe-resources-slim-docker",
           :node-id "agent"}
          {:parent  "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name    "agent.node-id",
           :value   "agent",
           :node-id "agent"}
          {:parent  "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name    "agent.service-id",
           :value
           "cd377e4afc0843f6f964d7f4f1d79f368a7096234ed29310cbbc054af7178eef",
           :node-id "agent"}
          {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name   "hostname",
           :value  "178.194.193.87"}
          {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name   "ip.local",
           :value  ""}
          {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name   "ip.public",
           :value  "178.194.193.87"}
          {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name   "ip.swarm",
           :value  "192.168.64.4"}
          {:parent "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433",
           :name   "ip.vpn",
           :value  ""}]
         (t/get-deployment-state {:id     "deployment/b3b70820-2de4-4a11-b00c-a79661c3d433"
                                  :module {:compatibility "docker-compose"
                                           :subtype       "application"}}
                                 minimal-nb-status)
         )))


;; to build a list of deployments states to be updated
;; do not start from coe-resources but from deployment
;; because compose apps has only a uuid registered as label "com.docker.compose.project".
;; so for each deployment depending on type dig into coe resources and status to get values to update them afterward
;; it has also the advantage to remove the need to check if deployment is really running on this NE

;; query deployments in state started
;; for each of deployment running on the NE depending on type check docker containers/services or kubernetes resources
;; build a bulk update query on parameters
