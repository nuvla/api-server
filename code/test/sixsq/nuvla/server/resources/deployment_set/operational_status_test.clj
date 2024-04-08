(ns sixsq.nuvla.server.resources.deployment-set.operational-status-test
  (:require [clojure.test :refer [deftest is testing]]
            [sixsq.nuvla.server.resources.common.utils :as u]
            [sixsq.nuvla.server.resources.deployment-set.operational-status :as t]))


(def app1-id "module/361945e2-36a8-4cb2-9d5d-6f0cef38a1f8")
(def app2-id "module/188555b1-2006-4766-b287-f60e5e908197")


(def app1-env-vars [{:name  "var_1_value"
                     :value "var1 overwritten in app set or deployment set"}
                    {:name  "var_2"
                     :value "var2 overwritten in app set or deployment set"}])
(def app1-env-vars* [{:name  "var_1_value"
                      :value "var1 overwritten in app set or deployment set"}
                     {:name  "var_2"
                      :value "modified later in deployment set"}])

(def app1-files [{:file-name    "file1"
                  :file-content "file1 overwritten in app set or deployment set"}
                 {:file-name    "file2"
                  :file-content "file2 overwritten in app set or deployment set"}])
(def app1-files* [{:file-name    "file1"
                   :file-content "file1 overwritten in app set or deployment set"}
                  {:file-name    "file2"
                   :file-content "modified later in deployment set"}])

(def target1-id "credential/72c875b6-9acd-4a54-b3aa-d95a2ed48316")
(def target2-id "credential/bc258c46-4771-45d3-9b38-97afdf185f44")
(def target3-id "credential/53b7ab49-3c4e-48eb-922b-e3067597b1cc")


(def deployment1
  {:app-set     "set-1"
   :application {:id                      app1-id
                 :version                 1
                 :environmental-variables app1-env-vars
                 :files                   app1-files}
   :target      target1-id})
(def deployment2
  {:app-set     "set-1"
   :application {:id                      app1-id
                 :version                 1
                 :environmental-variables app1-env-vars
                 :files                   app1-files}
   :target      target2-id})
(def deployment3
  {:app-set     "set-1"
   :application {:id      app2-id
                 :version 0}
   :target      target1-id})

;;
;; Expected deployment configurations
;;

(def expected #{deployment1 deployment2 deployment3})


;;
;; Utils
;;

(defn set-random-deployment-id
  [{:keys [id] :as deployment}]
  (cond-> deployment
          (nil? id) (assoc :id (u/rand-uuid))))


(defn set-random-deployment-ids
  [deployments]
  (->> deployments
       (map set-random-deployment-id)
       set))


(defn set-deployment-state
  [{:keys [state] :as deployment} new-state]
  (cond-> deployment
          (nil? state) (assoc :state new-state)))


(defn set-deployment-states
  [deployments state]
  (->> deployments
       (map #(set-deployment-state % state))
       set))


;;
;; Extra deployment
;;

(def extra-deployment-id "extra-deployment-id")
(def extra-deployment
  {:id          extra-deployment-id
   :app-set     "set-1"
   :application {:id                      app1-id
                 :version                 1
                 :environmental-variables app1-env-vars
                 :files                   app1-files}
   :target      target3-id})

;;
;; Updated deployments
;;

(def deployment1*
  (-> deployment1
      (update-in [:application :environmental-variables]
                 (constantly app1-env-vars*))
      (update-in [:application :files]
                 (constantly app1-files*))
      set-random-deployment-id
      (set-deployment-state "STARTED")))

(def deployment2*
  (-> deployment2
      (update-in [:application :version] inc)
      set-random-deployment-id
      (set-deployment-state "STARTED")))

(def deployment2**
  (-> deployment2
      set-random-deployment-id
      (set-deployment-state (rand-nth ["CREATED", "STARTING", "STOPPING", "STOPPED", "PAUSING", "PAUSED",
                                       "SUSPENDING", "SUSPENDED", "UPDATING", "PENDING", "ERROR"]))))

(def deployment1? (set-random-deployment-id deployment1))

;;
;; Current deployment configurations
;;
;; + suffix indicates additional deployment (with respect to expected)
;; - suffix indicates missing deployment    (with respect to expected)
;; * suffix indicates updated deployment    (with respect to expected)
;; ? suffix indicates duplicated deployment (with respect to expected)

(defn ->current
  "Converts an expected deployment entry to a current deployment entry
   by adding `id` and state keys."
  [deployments]
  (-> deployments
      set-random-deployment-ids
      (set-deployment-states "STARTED")))


(def =expected
  (->current expected))


(def extra-deployment+
  (-> #{deployment1 deployment2 deployment3 extra-deployment}
      ->current))


(def deployment1*_deployment2*
  (->> #{deployment1* deployment2* deployment3}
       ->current))

(def deployment1*_deployment2**
  (->> #{deployment1* deployment2** deployment3}
       ->current))

(def deployment1-_extra-deployment+
  (-> #{deployment2 deployment3 extra-deployment}
      ->current))

(def deployment1-_deployment2*
  (->> #{deployment2* deployment3}
       ->current))

(def deployment2*_extra-deployment+
  (-> #{deployment1 deployment2* deployment3 extra-deployment}
      ->current))

(def deployment1-_deployment2*_extra-deployment+
  (-> #{deployment2* deployment3 extra-deployment}
      ->current))


;;
;; Divergence map tests
;;

(deftest divergence-map
  (testing "Current matches expected"
    (is (= {}
           (t/divergence-map expected =expected))))
  (testing "Empty current"
    (is (= {:deployments-to-add expected}
           (t/divergence-map expected #{}))))
  (testing "Current contains a deployment that should not exist"
    (is (= {:deployments-to-remove #{extra-deployment-id}}
           (t/divergence-map expected extra-deployment+))))
  (testing "Some deployments need to be updated"
    (is (= {:deployments-to-update #{[deployment1* deployment1]
                                     [deployment2* deployment2]}}
           (t/divergence-map expected deployment1*_deployment2*)))
    (is (= {:deployments-to-update #{[deployment1* deployment1]
                                     [deployment2** deployment2]}}
           (t/divergence-map expected deployment1*_deployment2**))))
  (testing "Deployment with UPDATED state matches expected"
    (is (= {:deployments-to-add #{deployment2 deployment3}}
           (t/divergence-map expected
                             (-> [deployment1]
                                 set-random-deployment-ids
                                 (set-deployment-states "UPDATED"))))))
  (testing "More combinations"
    (is (= {:deployments-to-add    #{deployment1}
            :deployments-to-remove #{extra-deployment-id}}
           (t/divergence-map expected deployment1-_extra-deployment+)))
    (is (= {:deployments-to-add    #{deployment1}
            :deployments-to-update #{[deployment2* deployment2]}}
           (t/divergence-map expected deployment1-_deployment2*)))
    (is (= {:deployments-to-remove #{extra-deployment-id}
            :deployments-to-update #{[deployment2* deployment2]}}
           (t/divergence-map expected deployment2*_extra-deployment+)))
    (is (= {:deployments-to-add    #{deployment1}
            :deployments-to-remove #{extra-deployment-id}
            :deployments-to-update #{[deployment2* deployment2]}}
           (t/divergence-map expected deployment1-_deployment2*_extra-deployment+))))
  (testing "Duplicated deployments in current state should be removed"
    (let [current [deployment1* deployment2* deployment1?]]
      (is (= {:deployments-to-add    #{deployment3}
              :deployments-to-remove #{(:id deployment1?)}
              :deployments-to-update #{[deployment1* deployment1]
                                       [deployment2* deployment2]}}
             (t/divergence-map expected current))))))
