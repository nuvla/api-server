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
  (log/error "COMPRESSION: (content-encoding request)" (content-encoding request))
  (log/error "COMPRESSION: (-> request :headers)" (-> request :headers))
  (log/error "COMPRESSION: (-> request :headers (get \"content-encoding\"))" (-> request :headers (get "content-encoding")))
  (log/error "COMPRESSION: (-> request :headers :content-encoding)" (-> request :headers :content-encoding))
  (log/error "COMPRESSION: (= (content-encoding request) \"gzip\")" (= (content-encoding request) "gzip"))
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


(def req {:aleph/request-arrived 89256021799124,
          :aleph/keep-alive? true,
          :cookies "",
          :remote-addr "10.0.1.27",
          :headers {"content-encoding" "gzip", "x-forwarded-host" "194.182.161.53", "host" "194.182.161.53", "user-agent" "python-requests/2.26.0",
                    "cookie" "com.sixsq.nuvla.cookie--QSqdIQW04UwM-4o9ei2v_-", "content-length" "49", "x-forwarded-port" "443",
                    "accept" "application/json", "x-forwarded-for" "10.0.0.2", "accept-encoding" "gzip",
                    "x-forwarded-proto" "https", "x-real-ip" "10.0.0.2", "x-forwarded-server" "e0690bf0d143"}, :server-port 8200, :uri "/api/email", :server-name "ffdf14b63997",
          :query-string nil, :body "java.io.ByteArrayInputStream@6fcfdafc", :scheme :http, :request-method :post})