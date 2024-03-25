(ns sixsq.nuvla.utils.log-time
  (:require [clojure.tools.logging :as log]))

(defmacro logtime
  "Evaluates expr and log as error the time it took. Returns the value of expr."
  [msg expr]
  `(let [start# (. System (nanoTime))]
     (try
       (let [ret#     ~expr
             elapsed# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
         (log/error (str ~msg " -> Elapsed time: " elapsed# " msecs"))
         ret#)
       (catch Throwable t#
         (let [elapsed# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
           (log/error (str ~msg " Exception!! -> Elapsed time: " elapsed# " msecs"))
           (throw t#))))))

(defn ->logf
  [p0 msg f & args]
  (logtime msg (apply f (cons p0 args))))

(defn ->>logf
  [msg f & args]
  (logtime msg (apply f args)))

(defmacro logtime1
  "Like logtime but also returns the elapsed time in msecs and does not log."
  [expr]
  `(let [start# (. System (nanoTime))]
     (try
       (let [ret#     ~expr
             elapsed# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]
         [elapsed# ret#])
       (catch Throwable t#
         (throw t#)))))

