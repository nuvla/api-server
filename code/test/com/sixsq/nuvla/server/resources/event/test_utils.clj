(ns com.sixsq.nuvla.server.resources.event.test-utils
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.general :as gen-util]
    [com.sixsq.nuvla.server.util.time :as time]
    [jsonista.core :as j]
    [peridot.core :refer [content-type header request session]]))


(defn- urlencode-param
  [p]
  (->> (re-seq #"([^=]*)=(.*)" p)
       first
       next
       (map gen-util/encode-uri-component)
       (str/join "=")))


(defn urlencode-params
  [query-string]
  (if (empty? query-string)
    query-string
    (let [params (subs query-string 1)]
      (->> (str/split params #"&")
           (map urlencode-param)
           (str/join "&")
           (str "?")))))


(defn exec-request
  ([uri query-string auth-name]
   (exec-request uri query-string auth-name :get nil))

  ([uri query-string auth-name http-verb body]
   (-> (ltu/ring-app)
       session
       (content-type "application/json")
       (header authn-info-header (str/join " " [auth-name " " auth-name "group/nuvla-user" "group/nuvla-anon"]))
       (request (str uri (urlencode-params query-string))
                :body (some-> body j/write-value-as-string)
                :request-method http-verb
                :content-type "application/json")
       (ltu/body->edn))))


(defn is-count
  ([uri expected-count query-string auth-name]
   (-> (exec-request uri query-string auth-name)
       (ltu/is-status 200)
       (ltu/is-key-value :count expected-count))))


(defn are-counts
  ([key-to-count base-uri auth-name expected-count query-string]
   (are-counts key-to-count base-uri auth-name expected-count expected-count query-string))
  ([key-to-count base-uri auth-name expected-count expected-paginated-count query-string]
   (-> (exec-request base-uri query-string auth-name)
       (ltu/is-status 200)
       (ltu/is-key-value :count expected-count)
       (ltu/is-key-value count key-to-count expected-paginated-count))))


(def not-before? (complement time/before?))


(defn ordered-desc?
  [timestamps]
  (every? (fn [[a b]] (not-before? (time/parse-date a) (time/parse-date b))) (partition 2 1 timestamps)))


(def not-after? (complement time/after?))


(defn ordered-asc?
  [timestamps]
  (every? (fn [[a b]] (not-after? (time/parse-date a) (time/parse-date b))) (partition 2 1 timestamps)))


