(ns com.sixsq.nuvla.server.resources.lifecycle-test-utils
  (:require
    [clj-test-containers.core :as tc]
    [clojure.pprint :refer [pprint]]
    [clojure.string :as str]
    [clojure.test :refer [is join-fixtures]]
    [clojure.tools.logging :as log]
    [com.sixsq.nuvla.db.es.binding :as esb]
    [com.sixsq.nuvla.db.es.common.utils :as escu]
    [com.sixsq.nuvla.db.es.utils :as esu]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.app.routes :as routes]
    [com.sixsq.nuvla.server.app.server :as server]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [wrap-authn-info]]
    [com.sixsq.nuvla.server.middleware.base-uri :refer [wrap-base-uri]]
    [com.sixsq.nuvla.server.middleware.cimi-params :refer [wrap-cimi-params]]
    [com.sixsq.nuvla.server.middleware.eventer :refer [wrap-eventer]]
    [com.sixsq.nuvla.server.middleware.exception-handler :refer [wrap-exceptions]]
    [com.sixsq.nuvla.server.middleware.logger :refer [wrap-logger]]
    [com.sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
    [com.sixsq.nuvla.server.resources.event.utils :as event-utils]
    [com.sixsq.nuvla.server.util.kafka :as ka]
    [com.sixsq.nuvla.server.util.zookeeper :as uzk]
    [compojure.core :as cc]
    [jsonista.core :as j]
    [kinsky.embedded-kraft :as ke]
    [peridot.core :refer [request session]]
    [qbits.spandex :as spandex]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.keyword-params :refer [wrap-keyword-params]]
    [ring.middleware.nested-params :refer [wrap-nested-params]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.util.codec :as codec]
    [zookeeper :as zk]))

(def ^:private es-node-client-cache (atom nil))

(def es-port 9200)
(def zk-port 2181)

(server/init-logging)

(defn es-node
  []
  (first @es-node-client-cache))

(defn es-client
  []
  (second @es-node-client-cache))

(defn container-endpoint
  [container port]
  (str "localhost:" (get (:mapped-ports container) port)))

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

(defmacro is-header
  [m header value]
  `((fn [m# header# value#]
      (let [actual# (get-in m# [:response :headers header#])]
        (is (= value# actual#) (str "Expecting header " header# " with value " value# " got " (or actual# "nil") ". Message: "
                                    (get-in m# [:response :body :message])))
        m#)) ~m ~header ~value))

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
    (let [updated-body (if (str/blank? body-content)
                         {}
                         (j/read-value body-content j/keyword-keys-object-mapper))]
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
  (let [client (es-client)]
    (spandex/request client {:url [:_refresh], :method :post})))


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

(defn create-zk-server
  [port]
  (log/info "creating zookeeper server on port" port)
  (tc/start!
    (tc/create {:image-name    "zookeeper:3.4.13"
                :exposed-ports [port]})))

(defn create-zk-client-server
  []
  (let [server (create-zk-server zk-port)
        client (zk/connect (container-endpoint server zk-port))]
    (uzk/set-client! client)
    [client server]))

(defonce ^:private zk-client-server-cache (atom nil))


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

;;
;; Handling of Elasticsearch node and client for tests
;;

(defn create-test-node
  "Creates a local elasticsearch node that holds data that can be access
   through the native or HTTP protocols."
  ([port]
   (create-test-node (random-string) port))
  ([^String cluster-name port]
   (log/warn "creating ES server on port" port)
   (tc/start!
     (tc/create {:image-name    "docker.elastic.co/elasticsearch/elasticsearch:8.11.3"
                 :env-vars      {"ES_JAVA_OPTS"                                          "-Xms512m -Xmx512m"
                                 "xpack.security.enabled"                                "false"
                                 "discovery.type"                                        "single-node"
                                 "cluster.routing.allocation.disk.watermark.low"         "1gb"
                                 "cluster.routing.allocation.disk.watermark.high"        "500mb"
                                 "cluster.routing.allocation.disk.watermark.flood_stage" "100mb"
                                 "cluster.name"                                          cluster-name}
                 :exposed-ports [port]}))))

(defn es-test-endpoint
  [node]
  [(container-endpoint node es-port)])

(defn create-es-node-client
  []
  (let [node   (create-test-node es-port)
        client (esu/wait-for-cluster
                 (esu/create-es-client (es-test-endpoint node)))]
    [node client]))

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

(defn profile
  [msg f & rest]
  (let [ts (System/currentTimeMillis)]
    (log/debug (str "--->: " msg))
    (let [res (if rest
                (apply f rest)
                (f))]
      (log/debug (str "--->: " msg " done in: " (- (System/currentTimeMillis) ts)))
      res)))


(defmacro with-test-es-client
  "Creates an Elasticsearch test client, executes the body with the created
   client bound to the Elasticsearch client binding, and then clean up the
   allocated resources by closing both the client and the node."
  [& body]
  `(let [[_# client# sniffer#]
         (profile "setting es node client cache" set-es-node-client-cache)]
     (db/set-impl! (esb/->ElasticsearchRestBinding client# sniffer#))
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
      wrap-exceptions
      (wrap-json-body {:keywords? true})
      wrap-eventer
      wrap-authn-info
      (wrap-json-response {:pretty true :escape-non-ascii true})
      wrap-logger))


(defonce ^:private ring-app-cache (atom nil))

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


(def kafka-host "127.0.0.1")
(def kafka-port 9093)


(defn with-test-kafka-fixture
  [f]
  (log/debug "executing with-test-kafka-fixture")
  (let [log-dir (ke/create-tmp-dir "kraft-combined-logs")
        kafka   (profile "start kafka"
                         ke/start-embedded-kafka
                         {::ke/host          kafka-host
                          ::ke/port          kafka-port
                          ::ke/log-dirs      (str log-dir)
                          ::ke/server-config {"auto.create.topics.enable" "true"
                                              "transaction.timeout.ms"    "5000"}})]
    (try
      (when (= 0 (count @ka/producers!))
        (profile "create kafka producers"
                 ka/create-producers! (format "%s:%s" kafka-host kafka-port)))
      (profile "run supplied function" f)
      (catch Throwable t
        (throw t))
      (finally
        (ka/close-producers!)
        (log/debug "finalising with-test-kafka-fixture")
        ;; FIXME: Closing Kafka server takes ~6 sec. Instead of closing Kafka
        ;; server, delete all the topics. In case of the last test, the server
        ;; will just go down with the JVM.
        (let [ts (System/currentTimeMillis)]
          (.close kafka)
          (log/debug (str "--->: close kafka done in: "
                          (- (System/currentTimeMillis) ts))))
        (ke/delete-dir log-dir)))))


(def ^:private resources-initialised (atom false))


(defn initialize-indices
  []
  (if @resources-initialised
    (dyn/initialize-data)
    (do
      (dyn/initialize)
      (reset! resources-initialised true))))


;;
;; test fixture that starts the following parts of the test server:
;; elasticsearch, zookeeper, ring application
;;

(defn with-test-server-fixture
  "This fixture will ensure that Elasticsearch and zookeeper instances are
   running. It will also create a ring application and initialize it. The
   servers and application are cached to eliminate unnecessary instance
   creation for the subsequent tests."
  [f]
  (log/debug "executing with-test-server-fixture")
  (profile "start zookeeper" set-zk-client-server-cache)
  (with-test-es-client
    (profile "start ring app" ring-app)
    (profile "cleanup indices" esu/cleanup-index (es-client) escu/index-prefix-wildcard)
    (profile "initialize indices" initialize-indices)
    (profile "run supplied function" f)))


;;
;; test fixture that starts all parts of the test server including kafka
;;

(def with-test-server-kafka-fixture (join-fixtures [with-test-server-fixture
                                                    with-test-kafka-fixture]))

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
                   :body (j/write-value-as-string {:dummy "value"}))
          (is-status 405)))))

;;
;; ACL
;;

(defmacro is-acl
  [expected-acl actual-acl]
  `(do
     (when (:owners ~expected-acl)
       (is (= (set (:owners ~expected-acl)) (set (:owners ~actual-acl)))))
     (when (:edit-acl ~expected-acl)
       (is (= (set (:edit-acl ~expected-acl)) (set (:edit-acl ~actual-acl)))))
     (when (:edit-data ~expected-acl)
       (is (= (set (:edit-data ~expected-acl)) (set (:edit-data ~actual-acl)))))
     (when (:edit-meta ~expected-acl)
       (is (= (set (:edit-meta ~expected-acl)) (set (:edit-meta ~actual-acl)))))
     (when (:view-acl ~expected-acl)
       (is (= (set (:view-acl ~expected-acl)) (set (:view-acl ~actual-acl)))))
     (when (:view-data ~expected-acl)
       (is (= (set (:view-data ~expected-acl)) (set (:view-data ~actual-acl)))))
     (when (:view-meta ~expected-acl)
       (is (= (set (:view-meta ~expected-acl)) (set (:view-meta ~actual-acl)))))
     (when (:manage ~expected-acl)
       (is (= (set (:manage ~expected-acl)) (set (:manage ~actual-acl)))))
     (when (:delete ~expected-acl)
       (is (= (set (:delete ~expected-acl)) (set (:delete ~actual-acl)))))))


;;
;; events
;;

(defmacro is-event
  [expected-event actual-event]
  `(let [expected-authn-info# (:authn-info ~expected-event)
         authn-info#          (:authn-info ~actual-event)]
     (is (some? ~actual-event))
     (when (:name ~expected-event)
       (is (= (:name ~expected-event) (:name ~actual-event))))
     (when (:description ~expected-event)
       (is (= (:description ~expected-event) (:description ~actual-event))))
     (when (:category ~expected-event)
       (is (= (:category ~expected-event) (:category ~actual-event))))
     (when expected-authn-info#
       (is (= (:user-id expected-authn-info#) (:user-id authn-info#)))
       (is (= (:active-claim expected-authn-info#) (:active-claim authn-info#)))
       (is (= (set (:claims expected-authn-info#)) (set (:claims authn-info#)))))
     (when (:linked-identifiers ~expected-event)
       (is (= (set (:linked-identifiers ~expected-event))
              (set (get-in ~actual-event [:content :linked-identifiers])))))
     (when (some? (:success ~expected-event))
       (is (= (:success ~expected-event) (:success ~actual-event))))
     (when (some? (:acl ~expected-event))
       (is-acl (:acl ~expected-event) (:acl ~actual-event)))))

(defn refresh-index-query-events
  [resource-id opts]
  (refresh-es-indices)
  (event-utils/query-events resource-id opts))

(defmacro is-last-event
  [resource-id expected-event]
  `(let [event# (last (refresh-index-query-events ~resource-id {:orderby [["timestamp" :desc]] :last 1}))]
     (is-event ~expected-event event#)))


(defmacro are-last-events
  [resource-id expected-events]
  `(let [events# (take (count ~expected-events)
                       (refresh-index-query-events ~resource-id {:orderby [["timestamp" :desc]]
                                                                 :last    (count ~expected-events)}))]
     (is (= (count ~expected-events) (count events#)))
     (doall (map (fn [expected-event# actual-event#]
                   (is-event expected-event# actual-event#))
                 ~expected-events
                 events#))))
