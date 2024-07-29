(ns com.sixsq.nuvla.server.middleware.gzip
  (:require [clojure.java.io :as io])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.util.zip GZIPInputStream)))


(defn gunzip
  [body]
  (let [output (ByteArrayOutputStream.)]
    (with-open [input (-> body io/input-stream GZIPInputStream.)]
      (io/copy input output)
      (ByteArrayInputStream. (.toByteArray output)))))


(defn content-encoding
  [request]
  (get-in request [:headers "content-encoding"]))


(defn gzip?
  [request]
  (= (content-encoding request) "gzip"))


(defn gzip-uncompress
  [request]
  (-> request
      (update :body gunzip)
      (update :headers dissoc "content-encoding")))


(defn wrap-gzip-uncompress
  [handler]
  (fn [request]
    (let [gzipped? (gzip? request)]
      (-> request
          (cond-> gzipped? gzip-uncompress)
          handler))))
