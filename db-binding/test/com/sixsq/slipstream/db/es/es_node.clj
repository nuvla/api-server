(ns com.sixsq.slipstream.db.es.es-node
  "Utility for creating an Elasticsearch node that has an HTTP interface for
   tests. This is copied from the larger DB testing module to avoid a circular
   dependency."
  (:require
    [me.raynes.fs :as fs])
  (:import
    (java.util UUID)
    (org.elasticsearch.common.logging LogConfigurator)
    (org.elasticsearch.common.settings Settings)
    (org.elasticsearch.node MockNode)
    (org.elasticsearch.transport Netty4Plugin)))


(defn create-test-node
  "Creates a local elasticsearch node that holds data that can be access
   through the native or HTTP protocols."
  ([]
   (create-test-node (str (UUID/randomUUID))))
  ([^String cluster-name]
   (let [tempDir (str (fs/temp-dir "es-data-"))
         settings (.. (Settings/builder)
                      (put "cluster.name" cluster-name)
                      (put "action.auto_create_index" true)
                      (put "path.home" tempDir)
                      (put "transport.netty.worker_count" 3)
                      (put "node.data" true)
                      (put "http.enabled" true)
                      (put "logger.level" "ERROR")
                      (put "http.type" "netty4")
                      (put "http.port" "9200-9300")
                      (put "transport.type" "netty4")
                      (put "network.host" "127.0.0.1")
                      (build))
         plugins [Netty4Plugin]]

     (LogConfigurator/configureWithoutConfig settings)
     (.. (MockNode. ^Settings settings plugins)
         (start)))))
