(ns com.sixsq.slipstream.ssclj.resources.event-test
  (:require
    [clojure.data.json :as json]
    [clojure.test :refer :all]
    [com.sixsq.slipstream.ssclj.app.params :as p]
    [com.sixsq.slipstream.ssclj.middleware.authn-info-header :refer [authn-info-header]]
    [com.sixsq.slipstream.ssclj.resources.common.utils :as u]
    [com.sixsq.slipstream.ssclj.resources.event :refer :all]
    [com.sixsq.slipstream.ssclj.resources.event.test-utils :as tu :refer [exec-request is-count urlencode-params]]
    [com.sixsq.slipstream.ssclj.resources.lifecycle-test-utils :as ltu]
    [peridot.core :refer :all]
    [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
    [ring.middleware.params :refer [wrap-params]]))

(def base-uri (str p/service-context (u/de-camelcase resource-name)))

(def ^:private nb-events 20)

(def valid-event {:acl       {:owner {:type "USER" :principal "joe"}
                              :rules [{:type "USER" :principal "joe" :right "ALL"}]}
                  :timestamp "2015-01-16T08:05:00.0Z"
                  :content   {:resource {:href "run/45614147-aed1-4a24-889d-6365b0b1f2cd"}
                              :state    "Started"}
                  :type      "state"
                  :severity  "critical"})

(def valid-events
  (for [i (range nb-events)]
    (-> valid-event
        (assoc-in [:content :resource :href] (str "run/" i))
        (assoc :timestamp (if (even? i) "2016-01-16T08:05:00.0Z" "2015-01-16T08:05:00.0Z")))))

(defn insert-some-events-fixture!
  [f]
  (let [app (ltu/ring-app)
        state (-> app
                  (session)
                  (content-type "application/json")
                  (header authn-info-header "joe"))]
    (doseq [valid-event valid-events]
      (request state base-uri
               :request-method :post
               :body (json/write-str valid-event))))
  (f))

(use-fixtures :each (join-fixtures [ltu/with-test-server-fixture
                                    insert-some-events-fixture!]))

;;
;; Note that these tests need nb-events > 5
;;

(def ^:private are-counts
  (partial tu/are-counts :events base-uri "joe"))

(deftest events-are-retrieved-most-recent-first
  (->> valid-events
       (map :timestamp)
       tu/ordered-desc?
       false?
       is)

  (->> (get-in (exec-request base-uri "" "joe") [:response :body :events])
       (map :timestamp)
       tu/ordered-desc?
       is))

(deftest check-events-can-be-reordered
  (->> (get-in (exec-request base-uri "?$orderby=timestamp:asc" "joe") [:response :body :events])
       (map :timestamp)
       tu/ordered-asc?
       is))

(defn timestamp-paginate-single
  [n]
  (-> (exec-request base-uri (str "?$first=" n "&$last=" n) "joe")
      (get-in [:response :body :events])
      first
      :timestamp))

;; Here, timestamps are retrieved one by one (due to pagination)
(deftest events-are-retrieved-most-recent-first-when-paginated
  (-> (map timestamp-paginate-single (range 1 (inc nb-events)))
      tu/ordered-desc?
      is))

(deftest resources-pagination
  (are-counts nb-events "")
  ;; two different counts are checked
  ;; first one should be not impacted by pagination (so we expect nb-events)
  ;; second is the count after pagination (0 in that case with a bogus pagination)
  (are-counts nb-events 0 "?$first=10&$last=5")
  (are-counts nb-events (- nb-events 2) "?$first=3")
  (are-counts nb-events 2 "?$last=2")
  (are-counts nb-events 2 "?$first=3&$last=4"))

(deftest pagination-occurs-after-filtering
  (are-counts 1 "?$filter=content/resource/href='run/5'")
  (are-counts 1 "?$filter=content/resource/href='run/5'&$last=1")
  (are-counts 1 "?$last=1&$filter=content/resource/href='run/5'"))

(deftest resources-filtering
  (doseq [i (range nb-events)]
    (are-counts 1 (str "?$filter=content/resource/href='run/" i "'")))
  (are-counts 0 "?$filter=content/resource/href='run/100'")

  (are-counts 1 "?$filter=content/resource/href='run/3' and type='state'")
  (are-counts 1 "?$filter=type='state' and content/resource/href='run/3'")
  (are-counts 1 "?$filter=type='state'       and     content/resource/href='run/3'")

  (are-counts 1 "?$filter=content/resource/href='run/3'")
  (are-counts 0 "?$filter=type='WRONG' and content/resource/href='run/3'")
  (are-counts 0 "?$filter=content/resource/href='run/3' and type='WRONG'")
  (are-counts nb-events "?$filter=type='state'"))

(deftest filter-and
  (are-counts nb-events "$filter=type='state' and timestamp='2015-01-16T08:05:00.0Z'")
  (are-counts 0 "?$filter=type='state' and type='XXX'")
  (are-counts 0 "?$filter=type='YYY' and type='state'")
  (are-counts 0 "?$filter=(type='state') and (type='XXX')")
  (are-counts 0 "?$filter=(type='YYY') and (type='state')"))

(deftest filter-or
  (are-counts 0 "?$filter=type='XXX'")
  (are-counts nb-events "?$filter=type='state'")
  (are-counts nb-events "?$filter=type='state' or type='XXXX'")
  (are-counts nb-events "?$filter=type='XXXX' or type='state'")
  (are-counts nb-events "?$filter=(type='state') or (type='XXX')")
  (are-counts nb-events "?$filter=(type='XXXXX') or (type='state')")
  (are-counts 0 "?$filter=type='XXXXX' or type='YYYY'")
  (are-counts 0 "?$filter=(type='XXXXX') or (type='YYYY')"))

(deftest filter-multiple
  (are-counts 0 "?$filter=type='state'&$filter=type='XXX'")
  (are-counts 1 "?$filter=type='state'&$filter=content/resource/href='run/3'"))

(deftest filter-nulls
  (are-counts nb-events "?$filter=type!=null")
  (are-counts nb-events "?$filter=null!=type")
  (are-counts 0 "?$filter=type=null")
  (are-counts 0 "?$filter=null=type")
  (are-counts nb-events "?$filter=(unknown=null)and(type='state')")
  (are-counts nb-events "?$filter=(content/resource/href!=null)and(type='state')"))

(deftest filter-prefix
  (are-counts nb-events "?$filter=type^='st'")
  (are-counts nb-events "?$filter=content/resource/href^='run/'")
  (are-counts 0 "?$filter=type^='stXXX'")
  (are-counts 0 "?$filter=content/resource/href^='XXX/'"))

(deftest filter-wrong-param
  (-> (exec-request base-uri "?$filter=type='missing end quote" "joe")
      (ltu/is-status 400)
      (get-in [:response :body :message])
      (.startsWith "Invalid CIMI filter. Parse error at line 1, column 7")
      is))
