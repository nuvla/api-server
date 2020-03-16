(ns sixsq.nuvla.server.resources.lifecycle-test-utils
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.test :refer [is]]
    [clojure.tools.logging :as log]
    [compojure.core :as cc]
    [me.raynes.fs :as fs]
    [peridot.core :refer [request session]]
    [qbits.spandex :as spandex]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.nested-params :refer [wrap-nested-params]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.codec :as codec]
    [sixsq.nuvla.db.es.binding :as esb]
    [sixsq.nuvla.db.es.common.utils :as escu]
    [sixsq.nuvla.db.es.utils :as esu]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.app.routes :as routes]
    [sixsq.nuvla.server.middleware.authn-info :refer [wrap-authn-info]]
    [sixsq.nuvla.server.middleware.base-uri :refer [wrap-base-uri]]
    [sixsq.nuvla.server.middleware.cimi-params :refer [wrap-cimi-params]]
    [sixsq.nuvla.server.middleware.exception-handler :refer [wrap-exceptions]]
    [sixsq.nuvla.server.middleware.logger :refer [wrap-logger]]
    [sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
    [sixsq.nuvla.server.util.zookeeper :as uzk]
    [zookeeper :as zk])
  (:import
    (java.util UUID)
    (org.apache.curator.test TestingServer)
    (org.elasticsearch.common.logging LogConfigurator)
    (org.elasticsearch.common.settings Settings)
    (org.elasticsearch.index.reindex ReindexPlugin)
    (org.elasticsearch.node MockNode)
    (org.elasticsearch.transport Netty4Plugin)))


(defn random-string
  "provides a random string with optional prefix"
  [& [prefix]]
  (apply str prefix (repeatedly 15 #(rand-nth "abcdefghijklmnopqrstuvwxyz"))))


(defn serialize-cookie-value
  "replaces the map cookie value with a serialized string"
  [{:keys [value] :as cookie}]
  (if value
    (assoc cookie :value (codec/form-encode value))
    cookie))


(defmacro message-matches
  [m re]
  `((fn [m# re#]
      (let [message# (get-in m# [:response :body :message])]
        (if (string? re#)
          (do
            (is (.startsWith (or message# "") re#) (str "Message does not start with string. " (or message# "nil") " " re#))
            m#)
          (do
            (is (re-matches re# message#) (str "Message does not match pattern. " " " re#))
            m#)))) ~m ~re))


(defmacro is-status
  [m status]
  `((fn [m# status#]
      (let [actual# (get-in m# [:response :status])]
        (is (= status# actual#) (str "Expecting status " status# " got " (or actual# "nil") ". Message: "
                                     (get-in m# [:response :body :message])))
        m#)) ~m ~status))


(defmacro is-key-value
  ([m f k v]
   `((fn [m# f# k# v#]
       (let [actual# (-> m# :response :body k# f#)]
         (is (= v# actual#) (str "Expecting " v# " got " (or actual# "nil") " for " k#))
         m#)) ~m ~f ~k ~v))
  ([m k v]
   `(is-key-value ~m identity ~k ~v)))


(defmacro has-key
  [m k]
  `((fn [m# k#]
      (is (get-in m# [:response :body k#]) (str "Map did not contain key " k#)) m#)
    ~m ~k))


(defmacro is-resource-uri
  [m type-uri]
  `(is-key-value ~m :resource-type ~type-uri))


(defn href->url
  [href]
  (when href
    (str p/service-context href)))


(defn get-op
  [m op]
  (->> (get-in m [:response :body :operations])
       (map (juxt :rel :href))
       (filter (fn [[rel _]] (= rel (name op))))
       first
       second))


(defn get-op-url
  [m op]
  (href->url (get-op m op)))


(defn select-op
  [m op]
  (let [op-list     (get-in m [:response :body :operations])
        defined-ops (map :rel op-list)]
    [(some #(= % (name op)) defined-ops) defined-ops]))


(defmacro is-operation-present
  [m expected-op]
  `((fn [m# expected-op#]
      (let [[op# defined-ops#] (select-op m# expected-op#)]
        (is op# (str "Missing " (name expected-op#) " in " defined-ops#))
        m#))
    ~m ~expected-op))


(defmacro is-operation-absent [m absent-op]
  `((fn [m# absent-op#]
      (let [[op# defined-ops#] (select-op m# absent-op#)]
        (is (nil? op#) (str "Unexpected op " absent-op# " in " defined-ops#)))
      m#)
    ~m ~absent-op))


(defmacro is-id
  [m id]
  `(is-key-value ~m :id ~id))


(defmacro is-count
  [m f]
  `((fn [m# f#]
      (let [count# (get-in m# [:response :body :count])]
        (is (number? count#) (str "Count is not a number: " count#))
        (when (number? count#)
          (if (fn? f#)
            (is (f# count#) "Function of count did not return truthy value")
            (is (= f# count#) (str "Count wrong, expecting " f# ", got " (or count# "nil")))))
        m#)) ~m ~f))


(defn does-body-contain
  [m v]
  `((fn [m# v#]
      (let [body# (get-in m# [:response :body])]
        (is (= (merge body# v#) body#))))
    ~m ~v))


(defmacro is-set-cookie
  [m]
  `((fn [m#]
      (let [cookies# (get-in m# [:response :cookies])
            n#       (count cookies#)
            token#   (-> (vals cookies#)
                         first
                         serialize-cookie-value
                         :value)]
        (is (= 1 n#) "incorrect number of cookies")
        (is (not= "INVALID" token#) "expecting valid token but got INVALID")
        (is (not (str/blank? token#)) "got blank token")
        m#)) ~m))


(defmacro is-unset-cookie
  [m]
  `((fn [m#]
      (let [cookies# (get-in m# [:response :cookies])
            n#       (count cookies#)
            token#   (-> (vals cookies#)
                         first
                         serialize-cookie-value
                         :value)]
        (is (= 1 n#) "incorrect number of cookies")
        (is (= "INVALID" token#) "expecting INVALID but got different value")
        (is (not (str/blank? token#)) "got blank token")
        m#)) ~m))


(defmacro is-location
  [m]
  `((fn [m#]
      (let [uri-header# (get-in m# [:response :headers "Location"])
            uri-body#   (get-in m# [:response :body :resource-id])]
        (is uri-header# "Location header was not set")
        (is uri-body# "Location (resource-id) in body was not set")
        (is (= uri-header# uri-body#) (str "!!!! Mismatch in locations, header=" uri-header# ", body=" uri-body#))
        m#)) ~m))


(defn location
  [m]
  (let [uri (get-in m [:response :headers "Location"])]
    (is uri "Location header missing from response")
    uri))


(defn location-url
  [m]
  (href->url (location m)))


(defmacro is-location-value
  [m v]
  `((fn [m# v#]
      (let [location# (location m#)]
        (is (= location# v#))))
    ~m ~v))


(defn operations->map
  [m]
  (into {} (map (juxt :rel :href) (:operations m))))


(defn body
  [m]
  (get-in m [:response :body]))


(defn body-resource-id
  [m]
  (get-in m [:response :body :resource-id]))


(defn body->edn
  [m]
  (if-let [body-content (body m)]
    (let [updated-body (if (string? body-content)
                         (json/read-str body-content :key-fn keyword :eof-error? false :eof-value {})
                         (json/read (io/reader body-content) :key-fn keyword :eof-error? false :eof-value {}))]
      (update-in m [:response :body] (constantly updated-body)))
    m))


(defn entries
  [m]
  (some-> m :response :body :resources))


(defn concat-routes
  [rs]
  (apply cc/routes rs))


(defn dump
  [response]
  (pprint response)
  response)


(defn dump-m
  [response message]
  (println "-->>" message)
  (pprint response)
  (println message "<<--")
  response)


(defn refresh-es-indices
  []
  (let [client (spandex/client {:hosts ["localhost:9200"]})]
    (spandex/request client {:url [:_refresh], :method :post})
    (spandex/close! client)))


(defn strip-unwanted-attrs
  "Strips common attributes that are not interesting when comparing
   versions of a resource."
  [m]
  (let [unwanted #{:id :resource-type :acl :operations
                   :created :updated :name :description :tags}]
    (into {} (remove #(unwanted (first %)) m))))


;;
;; Handling of Zookeeper server and client
;;

(defn create-zk-client-server
  []
  (let [port 21810]
    (log/info "creating zookeeper server on port" port)
    (let [server (TestingServer. port)
          client (zk/connect (str "127.0.0.1:" port))]
      (uzk/set-client! client)
      [client server])))


(def ^:private zk-client-server-cache (atom nil))


(defn set-zk-client-server-cache
  "Sets the value of the cached Elasticsearch node and client. If the current
   value is nil, then a new node and a new client are created and cached. If
   the value is not nil, then the cache is set to the same value. This returns
   the tuple with the node and client, which should never be nil."
  []
  ;; Implementation note: It is unfortunate that the atom will constantly be
  ;; reset to the current value because swap! is used.  Unfortunately,
  ;; compare-and-set! can't be used because we want to avoid unnecessary
  ;; creation of ring application instances.
  (swap! zk-client-server-cache (fn [current] (or current (create-zk-client-server)))))


(defn clear-zk-client-server-cache
  "Unconditionally clears the cached Elasticsearch node and client. Can be
   used to force the re-initialization of the node and client. If the current
   values are not nil, then the node and client will be closed, with errors
   silently ignored."
  []
  (let [[[client server] _] (swap-vals! zk-client-server-cache (constantly nil))]
    (when client
      (try
        (.close client)
        (catch Exception _)))
    (when server
      (try
        (.close server)
        (catch Exception _)))))


;;
;; Handling of Elasticsearch node and client for tests
;;


(defn create-test-node
  "Creates a local elasticsearch node that holds data that can be access
   through the native or HTTP protocols."
  ([]
   (create-test-node (str (UUID/randomUUID))))
  ([^String cluster-name]
   (let [tempDir  (str (fs/temp-dir "es-data-"))
         settings (.. (Settings/builder)
                      (put "cluster.name" cluster-name)
                      (put "action.auto_create_index" true)
                      (put "path.home" tempDir)
                      (put "transport.netty.worker_count" 3)
                      (put "node.data" true)
                      (put "logger.level" "ERROR")
                      (put "cluster.routing.allocation.disk.watermark.low" "1gb")
                      (put "cluster.routing.allocation.disk.watermark.high" "500mb")
                      (put "cluster.routing.allocation.disk.watermark.flood_stage" "100mb")
                      (put "http.type" "netty4")
                      (put "http.port" "9200")
                      (put "transport.type" "netty4")
                      (put "network.host" "127.0.0.1")
                      (build))
         plugins  [Netty4Plugin
                   ReindexPlugin]]

     (LogConfigurator/configureWithoutConfig settings)
     (.. (MockNode. ^Settings settings plugins)
         (start)))))


(defn create-es-node-client
  []
  (log/info "creating elasticsearch node and client")
  (let [node   (create-test-node)
        client (-> (esu/create-es-client)
                   esu/wait-for-cluster)
        sniffer (esu/create-es-sniffer client)]
    [node client sniffer]))


(def ^:private es-node-client-cache (atom nil))


(defn set-es-node-client-cache
  "Sets the value of the cached Elasticsearch node and client. If the current
   value is nil, then a new node and a new client are created and cached. If
   the value is not nil, then the cache is set to the same value. This returns
   the tuple with the node and client, which should never be nil."
  []
  ;; Implementation note: It is unfortunate that the atom will constantly be
  ;; reset to the current value because swap! is used.  Unfortunately,
  ;; compare-and-set! can't be used because we want to avoid unnecessary
  ;; creation of ring application instances.
  (swap! es-node-client-cache (fn [current] (or current (create-es-node-client)))))


(defn clear-es-node-client-cache
  "Unconditionally clears the cached Elasticsearch node and client. Can be
   used to force the re-initialization of the node and client. If the current
   values are not nil, then the node and client will be closed, with errors
   silently ignored."
  []
  (let [[[node client sniffer] _] (swap-vals! es-node-client-cache (constantly nil))]
    (when client
      (try
        (.close client)
        (catch Exception _)))
    (when sniffer
      (try
        (.close sniffer)
        (catch Exception _)))
    (when node
      (try
        (.close node)
        (catch Exception _)))))


(defmacro with-test-es-client
  "Creates an Elasticsearch test client, executes the body with the created
   client bound to the Elasticsearch client binding, and then clean up the
   allocated resources by closing both the client and the node."
  [& body]
  `(let [cache# (set-es-node-client-cache)
         client# (second cache#)
         sniffer# (nth cache# 2)]
     (db/set-impl! (esb/->ElasticsearchRestBinding client# sniffer#))
     (esu/reset-index client# (str escu/default-index-prefix "*"))
     ~@body))


;;
;; Ring Application Management
;;

(defn make-ring-app [resource-routes]
  (log/info "creating ring application")
  (-> resource-routes
      wrap-cimi-params
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-base-uri
      wrap-authn-info
      wrap-exceptions
      (wrap-json-body {:keywords? true})
      (wrap-json-response {:pretty true :escape-non-ascii true})
      wrap-logger))


(def ^:private ring-app-cache (atom nil))

(defn set-ring-app-cache
  "Sets the value of the cached ring application. If the current value is nil,
   then a new ring application is created and cached. If the value is not nil,
   then the cache is set to the same value. This returns the ring application
   value, which should never be nil."
  []
  ;; Implementation note: It is unfortunate that the atom will constantly be
  ;; reset to the current value because swap! is used.  Unfortunately,
  ;; compare-and-set! can't be used because we want to avoid unnecessary
  ;; creation of ring application instances.
  (swap! ring-app-cache (fn [current] (or current
                                          (make-ring-app (concat-routes [(routes/get-main-routes)]))))))


(defn clear-ring-app-cache
  "Unconditionally clears the cached ring application instance.  Can be used
   to force the re-initialization of the ring application."
  []
  (reset! ring-app-cache (constantly nil)))


(defn ring-app
  "Returns a standard ring application with the CIMI server routes. By
   default, only a single instance will be created and cached. The cache can be
   cleared with the `clean-ring-app-cache` function."
  []
  (set-ring-app-cache))


;;
;; test fixture to ensure that all parts of the test server are started
;; (elasticsearch, zookeeper, ring application)
;;

(defn with-test-server-fixture
  "This fixture will ensure that Elasticsearch and zookeeper instances are
   running. It will also create a ring application and initialize it. The
   servers and application are cached to eliminate unnecessary instance
   creation."
  [f]
  (log/debug "executing with-test-server-fixture")
  (set-zk-client-server-cache)                              ;; always setup the zookeeper client and server
  (with-test-es-client
    (ring-app)
    (log/info "forced initialization of ring application")
    (dyn/initialize)                                        ;; must always reinitialize after database has been cleared
    (f)))


;;
;; miscellaneous utilities
;;

(defn verify-405-status
  "The url-methods parameter must be a list of URL/method tuples. It is
  expected that any request with the method to the URL will return a 405
  status."
  [url-methods]
  (doall
    (for [[uri method] url-methods]
      (-> (ring-app)
          session
          (request uri
                   :request-method method
                   :body (json/write-str {:dummy "value"}))
          (is-status 405)))))


