(ns sixsq.nuvla.server.middleware.gzip
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import (java.util.zip GZIPInputStream)
           (java.io ByteArrayOutputStream ByteArrayInputStream)))


(defn gunzip
  [body]
  (let [body-stream (ByteArrayInputStream. (.toByteArray body))
        output (ByteArrayOutputStream.)]
    (with-open [input (-> body-stream io/input-stream GZIPInputStream.)]
     (io/copy input output)
     (.toString output))))


(defn content-encoding
  [request]
  (get-in request [:headers "content-encoding"]))


(defn gzip?
  [request]
  (= (content-encoding request) "gzip"))


(defn gzip-uncompress
  [request]
  (let [res (-> request
            (update :body gunzip)
            (update :headers dissoc "content-encoding"))]
    (log/error "COMPRESSION: gzip-uncompress: " res)
    res
    ))


(defn wrap-gzip-uncompress
  [handler]
  (fn [request]
    (let [gzipped? (gzip? request)]
      (when gzipped?
        (log/error "COMPRESSION: wrap-gzip-uncompress: " request))
      (-> request
          (cond-> gzipped? gzip-uncompress)
          handler))))
