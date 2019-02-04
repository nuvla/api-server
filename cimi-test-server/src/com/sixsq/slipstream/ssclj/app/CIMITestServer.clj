(ns com.sixsq.slipstream.ssclj.app.CIMITestServer
  "Java class containing static functions for managing a CIMI test server."
  (:gen-class
    :methods [^{:static true} [start [] void]
              ^{:static true} [stop [] void]
              ^{:static true} [refresh [] void]]))


(def ^:const server-ns 'com.sixsq.slipstream.ssclj.app.test-server)


(defn- resolve-fn
  "Requires the server namespace; resolves and returns the function identified
   with the given system."
  [s]
  (require server-ns)
  (-> server-ns
      find-ns
      (ns-resolve s)))


(defn -start
  []
  ((resolve-fn 'start)))


(defn -stop
  []
  ((resolve-fn 'stop)))


(defn -refresh
  []
  ((resolve-fn 'refresh-es-indices)))
