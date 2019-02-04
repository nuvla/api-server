(ns com.sixsq.slipstream.ssclj.app.test-server
  (:require
    [com.sixsq.slipstream.db.es.binding :as esb]
    [com.sixsq.slipstream.db.es.utils :as esu]
    [com.sixsq.slipstream.db.loader :as db-loader]
    [com.sixsq.slipstream.dbtest.es.utils :as esut]
    [com.sixsq.slipstream.ssclj.util.zookeeper :as zku]
    [metrics.core :refer [remove-all-metrics]]
    [sixsq.slipstream.server.ring-container :as rc]
    [zookeeper :as zk])
  (:import
    (org.apache.curator.test TestingServer)))


(def ^:dynamic *service-clients* nil)


(defn set-service-clients [m]
  (alter-var-root #'*service-clients* (constantly m)))


(def ^:dynamic *stop-server-fn* nil)


(defn set-stop-server-fn [f]
  (alter-var-root #'*stop-server-fn* (constantly f)))


(defn create-clients []
  (let [zk-port 12001
        zk-server (TestingServer. zk-port)
        zk-client (zk/connect (str "127.0.0.1:" zk-port))
        zk-create-client-fn (constantly zk-client)

        es-port 12002
        es-node (esut/create-test-node)
        es-client (-> es-node
                      esu/node-client
                      esu/wait-for-cluster)
        es-db-binding (esb/->ESBindingLocal es-client)]
    {:zk-port             zk-port
     :zk-server           zk-server
     :zk-client           zk-client
     :zk-create-client-fn zk-create-client-fn

     :es-port             es-port
     :es-node             es-node
     :es-client           es-client
     :es-db-binding       es-db-binding
     :es-db-binding-fn    (constantly es-db-binding)}))


(defn start-clients []
  (set-service-clients (create-clients)))


(defn stop-clients []
  (let [{:keys [zk-server es-node es-db-binding]} *service-clients*]
    (set-service-clients nil)
    (try
      (try
        (zku/close-client!)
        (finally
          (try
            (.close zk-server)
            (finally
              (try
                (.close es-db-binding)
                (finally
                  (.close es-node)))))))
      (catch Exception e
        (println "ERROR SHUTTING DOWN TEST CLIENTS")
        (.printStackTrace e)))))


(defn start []
  (start-clients)
  (let [ssclj-port 12003
        {:keys [zk-create-client-fn es-db-binding-fn]} *service-clients*]
    (with-redefs [db-loader/load-db-binding es-db-binding-fn
                  zku/create-client zk-create-client-fn]
      (set-stop-server-fn (rc/start "com.sixsq.slipstream.ssclj.app.server/init" ssclj-port)))
    (System/setProperty "ssclj.endpoint" (str "http://localhost:" ssclj-port))))


(defn stop []
  (when-let [stop-fn *stop-server-fn*]
    (set-stop-server-fn nil)
    (try
      (stop-fn)
      (catch Exception e
        (println "ERROR STOPPING SERVER: " (.getMessage e))))
    (stop-clients)
    (System/clearProperty "ssclj.endpoint")
    (remove-all-metrics)))


(defn refresh-es-indices
  []
  (esu/refresh-all-indices (:es-client *service-clients*)))
