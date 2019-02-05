(ns sixsq.nuvla.server.ring
  "Provides a simple, generic ring application server that can be started
   either via the static 'main' function (e.g. for running as a daemon) or via
   the 'start' function (e.g. for testing from the REPL)."
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log])
  (:import
    [java.io Closeable]
    [java.net InetSocketAddress])
  (:gen-class))


(def ^:const default-port 8200)


(def ^:const default-host "127.0.0.1")


(defn- log-and-throw
  "Logs a fatal error and then throws an exception with the given method and
   optional cause."
  ([msg]
   (log-and-throw msg nil))
  ([msg e]
   (log/fatal msg)
   (if e
     (throw (ex-info (str msg "\n" e) {}))
     (throw (ex-info msg {})))))


(defn- as-symbol
  "Converts argument to symbol or throws an exception."
  [s]
  (try
    (symbol s)
    (catch Exception e
      (log-and-throw (str "invalid symbol: " s) e))))


(defn- ns-and-var
  "Provides the namespace and name of the symbol as symbols. If either is nil,
   then an exception is thrown."
  [s]
  (let [result ((juxt namespace name) s)]
    (if (some nil? result)
      (log-and-throw (str "symbol (" s ") must be a complete, namespaced value"))
      (map symbol result))))


(defn- resolve-var
  "Resolves the var in the given namespace or throws a descriptive exception."
  [ns f]
  (try
    (ns-resolve (find-ns ns) f)
    (catch Exception e
      (log-and-throw (str "could not resolve " f " in namespace " ns) e))))


(defn- dyn-resolve
  "Dynamically requires the namespace of the given symbol and then resolves
   the name of the symbol in this namespace. Returns the var for the resolved
   symbol. Throws an exception if the symbol could not be resolved."
  [s]
  (let [[ns f] (ns-and-var (as-symbol s))]
    (try
      (require ns)
      (catch Exception e
        (log-and-throw (str "error requiring namespace (" ns "): " (.getMessage e)))))

    (if-let [result (resolve-var ns f)]
      result
      (log-and-throw (str "symbol (" s ") was not found")))))


(defn- hook
  "Creates a JVM shutdown hook that runs the given stopping function. Note
   that some logging may be lost depending on the state of the JVM when the
   hook is run."
  [shutdown-fn]
  (proxy [Thread] []
    (run [] (shutdown-fn))))


(defn- register-shutdown-hook
  "Registers a shutdown hook in the JVM to shutdown the application and
   application container cleanly."
  [shutdown-fn]
  (.. (Runtime/getRuntime)
      (addShutdownHook (hook shutdown-fn))))


(defn- create-shutdown-fn
  [^Closeable server finalization-fn]
  (fn [] (try
           (and finalization-fn (finalization-fn))
           (log/info "successfully finalized ring container")
           (catch Exception e
             (log/error "failure when finalizing ring container:" (str e)))
           (finally
             (try
               (and server (.close server))
               (log/info "successfully shutdown ring container")
               (catch Exception e
                 (log/error "failure when shutting down ring container:" (str e))))))))


(defn- start-container
  "Starts the aleph container with the given ring handler and on the given
   port. Returns the server object that has been started, which can be stopped
   by calling 'close' on the object."
  [handler ^long port host]
  (log/info "starting aleph application container on host:port" (str host ":" port))
  (let [start-server (dyn-resolve 'aleph.http/start-server)
        server (->> port
                    (InetSocketAddress. host)
                    (hash-map :socket-address)
                    (start-server handler))]
    (log/info "started aleph application container on host:port" (str host ":" port))
    server))


(defn- validate-host
  "If the argument is the a valid string representation of an IP address or
   host, the value is returned. Otherwise, the default host is returned."
  [host]
  (if (and (string? host) (not (str/blank? host)))
    host
    default-host))


(defn- parse-port
  "Parses the given value (string or int) as an integer and returns the value
   if it is a valid port number. For any invalid input, this function returns
   the default port."
  [s]
  (try
    (let [port (cond
                 (string? s) (Integer/valueOf ^String s)
                 (integer? s) (Integer/valueOf ^int s)
                 :else default-port)]
      (if (< 0 port 65536)
        port
        default-port))
    (catch Exception _
      default-port)))


(defn start
  "Starts the application and application server. Return a 'stop' function
   that will shutdown the application and server when called."
  [server-init & [port host]]
  (let [server-init-fn (dyn-resolve server-init)
        [handler finalization-fn] (server-init-fn)
        port (parse-port port)
        host (validate-host host)]

    (log/info "starting " server-init "on" host "and port" port)
    (log/info "java vendor: " (System/getProperty "java.vendor"))
    (log/info "java version: " (System/getProperty "java.version"))
    (log/info "java classpath: " (System/getProperty "java.class.path"))

    (let [server (start-container handler port host)
          shutdown-fn (create-shutdown-fn server finalization-fn)]

      (log/info "started" server-init "on" host "and port" port)
      shutdown-fn)))


(defn- server-cfg
  "Reads the server configuration from the environment. The variable
   NUVLA_SERVER_INIT must be defined. It is the namespaced symbol of the server
   initialization function.

   If the environmental variable NUVLA_SERVER_PORT is defined, the value will
   be used for the server port, presuming that it is valid. If the value is
   invalid, then the default port will be used.

   NOTE: This function is only called when starting the server from the main
   function. Starting the server from the REPL will use the values given to the
   start function."
  []
  (let [env (dyn-resolve 'environ.core/env)
        server-port (env :nuvla-server-port)
        server-host (env :nuvla-server-host)]
    (if-let [server-init (env :nuvla-server-init)]
      [server-init server-port server-host]
      (let [msg "NUVLA_SERVER_INIT is not defined"]
        (log/error msg)
        (throw (ex-info msg {}))))))


(defn -main
  "Function to start the web application as a daemon. The configuration of the
   server is taken from the environment. The environment must have
   NUVLA_SERVER_INIT defined. NUVLA_SERVER_PORT and NUVLA_SERVER_PORT may be
   defined."
  [& _]
  (let [[server-init server-port server-host] (server-cfg)
        shutdown-fn (start server-init server-port server-host)]
    (register-shutdown-hook shutdown-fn))

  ;; The server (started as a daemon thread) will exit immediately
  ;; if the main thread is allowed to terminate.  To avoid this,
  ;; indefinitely block this thread.  Stopping the service can only
  ;; be done externally through a SIGTERM signal.
  @(promise))
