(ns sixsq.nuvla.server.resources.deloyment-set.utils-test
  (:require
    [clojure.test :refer [deftest is]]
    [sixsq.nuvla.server.resources.deployment-set.utils :as t]
    [tilakone.core :as tk]))

(defmacro is-state-after-action
  [fsm action state]
  `((fn [fsm# action# state#]
      (let [new-fsm# (tk/apply-signal fsm# action#)
            actual#  (::tk/state new-fsm#)]
        (is (= state# actual#) (str "Expecting state " state# " got " (or actual# "nil") "."))
        new-fsm#)) ~fsm ~action ~state))

(defmacro is-transition-not-allowed
  [fsm action]
  `((fn [fsm# action#]
      (try
        (tk/apply-signal fsm# action#)
        (is false (format "Transition with action [%s] in state [%s] should not be allowed!"
                          action# (::tk/state fsm#)))
        (catch Exception _e#
          (is true)))
      fsm#) ~fsm ~action))

(deftest state-machine
  (-> t/state-machine
      (assoc ::tk/guard? (constantly true))
      (is-state-after-action t/action-start t/state-starting)
      (is-state-after-action t/action-ok t/state-started)
      (is-transition-not-allowed t/action-nok)
      (is-state-after-action t/action-update t/state-updating)))

(deftest action-name
  (is (= (t/action-job-name t/action-force-delete) "deployment_set_force_delete"))
  (is (= (t/action-job-name "multiple-dashes-action") "deployment_set_multiple_dashes_action")))
