(ns sixsq.nuvla.server.resources.deployment-set.fsm
  (:require [clojure.string :as str]
            [statecharts.core :as fsm]
            [sixsq.nuvla.server.resources.deployment-set.utils :as u]
            [statecharts.impl :as impl]
            [statecharts.service :as fsm-service]
            [tilakone.core :as tk :refer [_]]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [statecharts.store :as store])
  (:import (statecharts.service Service)
           (statecharts.store IStore)))

(defn state-to-str
  [{state :_state}]
  (-> state name str/upper-case))

(defn str-to-state
  [s]
  (-> s str/lower-case keyword))

(defrecord NuvlaStateStore [state*]
  IStore
  (unique-id [_ _state] :context)
  (initialize [_ machine opts]
    (if (nil? @state*)
      (reset! state* (impl/initialize machine opts))
      state*))
  (transition [_ machine _state event opts]
    ;; db write (state-to-str result transition state-to-str)
    ;; create event
    ;; swap! atom
    (swap! state*
           #(impl/transition machine {:_state %} event opts)))
  (get-state [_ _]
    @state*))

(defn create-store
  "A single-store stores the current value of a single state."
  [resource-state]
  (NuvlaStateStore. (atom {:_state (str-to-state resource-state)})))

(defn service
  ([fsm current-state]
   (service fsm nil current-state))
  ([fsm opts current-state]
   (let [{:keys [clock
                 transition-opts]} (merge (fsm-service/default-opts) opts)
         store (create-store current-state)]
     (Service. (fsm-service/attach-fsm-scheduler fsm store clock)
               store
               false
               clock
               transition-opts))))

(deftest aaa

  (let [machine (fsm/machine
                  {:id      :deployment-group
                   :initial :new
                   :states
                   {:new               {:on {:start  :starting
                                             :edit   {}
                                             :delete {}}}
                    :starting          {:on {:cancel :partially-started
                                             :ok     :started
                                             :nok    :partially-started}}
                    :started           {:on {:edit   {}
                                             :update :updating
                                             :stop   :stopping}}
                    :stopping          {:on {:cancel :partially-stopped
                                             :ok     :stopped
                                             :nok    :partially-stopped}}
                    :updating          {:on {:cancel :partially-updated
                                             :ok     :updated
                                             :nok    :partially-updated}}
                    :stopped           {:on {:edit   {}
                                             :delete {}
                                             :start  :starting}}
                    :updated           {:on {:edit   {}
                                             :stop   :stopping
                                             :update :updating}}
                    :partially-updated {:on {:edit   {}
                                             :stop   :stopping
                                             :update :updating}}
                    :partially-started {:on {:edit   {}
                                             :stop   :stopping
                                             :update :updating}}
                    :partially-stopped {:on {:edit         {}
                                             :force-delete {}
                                             :start        :starting}}}})
        s1      (fsm/initialize machine)
        s2      (fsm/transition machine s1 {:type :start})
        s3      (fsm/transition machine s2 {:type :cancel})]
    (prn s1)
    (prn s2)
    (prn s3)
    (prn "ICI " (fsm/transition machine {:_state :started} {:type :edit}))
    (try
      (fsm/transition machine s2 {:type :x})
      (catch Exception e
        (prn e)
        ))

    #_(let [ser-1 (service machine "STARTING")]
        (fsm/start ser-1)
        (prn ser-1)
        #_(fsm/start ser-1)
        (println (fsm/state ser-1))
        (fsm/send ser-1 :nokk)
        (println (fsm/state ser-1))
        )

    ;
    ;
    ;;; send events to trigger transitions
    ;(fsm/send service :timer)
    ;
    ;;; prints :green
    ;(println (fsm/value service))
    ;
    ;(fsm/send service :timer)
    ;;; prints :yellow
    ;(println (fsm/value service))

    )
  )

(def count-ab-states
  [{::tk/name        "NEW"
    ::tk/transitions [{::tk/on "start", ::tk/to "STARTING"}]}
   {::tk/name        "STARTING"
    ::tk/transitions [{::tk/on _}]}])

; FSM has states, a function to execute actions, and current state and value:

(def count-ab
  {::tk/states  [{::tk/name        "NEW"
                  ::tk/transitions [{::tk/on "start", ::tk/to "STARTING"}]}
                 {::tk/name        "STARTING"
                  ::tk/transitions [{::tk/on _}]}]
   ::tk/state   "NEW"})


(deftest tilakone
  (prn (tk/transfers-to count-ab "start"))
  (prn (-> count-ab
           (tk/apply-signal "x")
           (tk/apply-signal "x")))
  )