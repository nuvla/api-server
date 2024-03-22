(ns sixsq.nuvla.utils.log-memory
  (:require [clojure.tools.logging :as log])
  (:import (java.io ByteArrayOutputStream ObjectOutputStream)))

;; approximation
(defn obj-memory [obj]
  (let [baos (ByteArrayOutputStream.)]
    (with-open [oos (ObjectOutputStream. baos)]
      (.writeObject oos obj))
    (count (.toByteArray baos))))

(defn gc []
  (dotimes [_ 4] (System/gc)))

(defn used-memory []
  (let [runtime (Runtime/getRuntime)]
    (gc)
    (- (.totalMemory runtime) (.freeMemory runtime))))

(defmacro logmemory [msg expr]
  `(let [before# (used-memory)]
     (try
       (let [ret# ~expr
             after# (used-memory)]
         (log/error (str ~msg " -> Used memory: " (- after# before#) " bytes"))
         ret#)
       (catch Throwable t#
         (let [after# (used-memory)]
           (log/error (str ~msg "Exception!! -> Used memory: " (- after# before#) " bytes"))
           (throw t#))))))

(defn is-version-before-2?
  [nuvlabox]
  (< (:version nuvlabox) 2))

(defn measure [f]
  (let [before (used-memory)
        _ (def foo (binding [*in* (java.io.PushbackReader.
                                    (clojure.java.io/reader f))]
                     (read)))
        after (used-memory)]
    (- after before)))
