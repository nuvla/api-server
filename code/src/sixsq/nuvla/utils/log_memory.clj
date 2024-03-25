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

(defn used-memory-no-gc []
  (let [runtime (Runtime/getRuntime)]
    (- (.totalMemory runtime) (.freeMemory runtime))))

(defmacro logmemory [msg expr]
  `(let [before# (used-memory)]
     (try
       (let [ret# ~expr
             after# (used-memory)]
         (log/error (str ~msg " -> Used memory: " (- after# before#) " bytes"))
         ret#)
       (catch Throwable t#
         (let [after# (used-memory-no-gc)]
           (log/error (str ~msg "Exception!! -> Used memory: " (- after# before#) " bytes"))
           (throw t#))))))

(defmacro logmemory1 [expr]
  "Like logmemory but also returns the amount of memory used in bytes and does not log."
  `(let [before# (used-memory)]
     (try
       (let [ret# ~expr
             after# (used-memory)]
         [(- after# before#) ret#])
       (catch Throwable t#
         (throw t#)))))
