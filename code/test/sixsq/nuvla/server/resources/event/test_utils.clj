(ns sixsq.nuvla.server.resources.event.test-utils
  (:require
    [clj-time.core :as time]
    [clj-time.format :as time-fmt]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [is]]
    [peridot.core :refer :all]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.middleware.authn-info-header :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(defn to-time
  "Tries to parse the given string as a DateTime value.  Returns the DateTime
   instance on success and nil on failure."
  [s]
  (time-fmt/parse (:date-time time-fmt/formatters) s))


(defn- urlencode-param
  [p]
  (->> (re-seq #"([^=]*)=(.*)" p)
       first
       next
       (map rc/url-encode)
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
       (header authn-info-header (str/join " " [auth-name "group/nuvla-user" "group/nuvla-anon"]))
       (request (str uri (urlencode-params query-string))
                :body (some-> body json/write-str)
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
  (every? (fn [[a b]] (not-before? (to-time a) (to-time b))) (partition 2 1 timestamps)))


(def not-after? (complement time/after?))


(defn ordered-asc?
  [timestamps]
  (every? (fn [[a b]] (not-after? (to-time a) (to-time b))) (partition 2 1 timestamps)))


