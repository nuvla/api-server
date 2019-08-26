(ns sixsq.nuvla.server.resources.event-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [peridot.core :refer :all]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.event :refer :all]
    [sixsq.nuvla.server.resources.event.test-utils :as tu :refer [exec-request is-count urlencode-params]]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]))


(def base-uri (str p/service-context resource-type))


(def ^:private nb-events 20)


(def valid-event {:acl       {:owners ["user/joe"]}
                  :created   "2015-01-16T08:05:00.00Z"
                  :updated   "2015-01-16T08:05:00.00Z"
                  :timestamp "2015-01-16T08:05:00.00Z"
                  :content   {:resource {:href "run/45614147-aed1-4a24-889d-6365b0b1f2cd"}
                              :state    "Started"}
                  :category  "state"
                  :severity  "critical"})


(def valid-events
  (for [i (range nb-events)]
    (-> valid-event
        (assoc-in [:content :resource :href] (str "run/" i))
        (assoc :timestamp (if (even? i) "2016-01-16T08:05:00.00Z" "2015-01-16T08:05:00.00Z")))))


(defn insert-some-events-fixture!
  [f]
  (let [state (-> (ltu/ring-app)
                  session
                  (content-type "application/json")
                  (header authn-info-header (str/join " " ["user/joe" "group/nuvla-user" "group/nuvla-anon"])))]
    (doseq [valid-event valid-events]
      (-> state
          (request base-uri
                   :request-method :post
                   :body (json/write-str valid-event))
          (ltu/is-status 201))))
  (f))


(use-fixtures :once (join-fixtures [ltu/with-test-server-fixture
                                    insert-some-events-fixture!]))


;;
;; Note that these tests need nb-events > 5
;;

(def ^:private are-counts
  (partial tu/are-counts :resources base-uri "user/joe"))


(deftest events-are-retrieved-most-recent-first
  (->> valid-events
       (map :timestamp)
       tu/ordered-desc?
       false?
       is)

  (->> (exec-request base-uri "" "user/joe")
       ltu/entries
       (map :timestamp)
       tu/ordered-desc?
       is))


(deftest check-events-can-be-reordered
  (->> (exec-request base-uri "?orderby=timestamp:asc" "user/joe")
       ltu/entries
       (map :timestamp)
       (tu/ordered-asc?)
       (is)))


(defn timestamp-paginate-single
  [n]
  (-> (exec-request base-uri (str "?first=" n "&last=" n) "user/joe")
      ltu/entries
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
  (are-counts nb-events 0 "?first=10&last=5")
  (are-counts nb-events (- nb-events 2) "?first=3")
  (are-counts nb-events 2 "?last=2")
  (are-counts nb-events 2 "?first=3&last=4"))


(deftest pagination-occurs-after-filtering
  (are-counts 1 "?filter=content/resource/href='run/5'")
  (are-counts 1 "?filter=content/resource/href='run/5'&last=1")
  (are-counts 1 "?last=1&filter=content/resource/href='run/5'"))


(deftest resources-filtering
  (doseq [i (range nb-events)]
    (are-counts 1 (str "?filter=content/resource/href='run/" i "'")))
  (are-counts 0 "?filter=content/resource/href='run/100'")

  (are-counts 1 "?filter=content/resource/href='run/3' and category='state'")
  (are-counts 1 "?filter=category='state' and content/resource/href='run/3'")
  (are-counts 1 "?filter=category='state'       and     content/resource/href='run/3'")

  (are-counts 1 "?filter=content/resource/href='run/3'")
  (are-counts 0 "?filter=category='WRONG' and content/resource/href='run/3'")
  (are-counts 0 "?filter=content/resource/href='run/3' and category='WRONG'")
  (are-counts nb-events "?filter=category='state'"))


(deftest filter-and
  (are-counts nb-events "filter=category='state' and timestamp='2015-01-16T08:05:00Z'")
  (are-counts 0 "?filter=category='state' and category='XXX'")
  (are-counts 0 "?filter=category='YYY' and category='state'")
  (are-counts 0 "?filter=(category='state') and (category='XXX')")
  (are-counts 0 "?filter=(category='YYY') and (category='state')"))


(deftest filter-or
  (are-counts 0 "?filter=category='XXX'")
  (are-counts nb-events "?filter=category='state'")
  (are-counts nb-events "?filter=category='state' or category='XXXX'")
  (are-counts nb-events "?filter=category='XXXX' or category='state'")
  (are-counts nb-events "?filter=(category='state') or (category='XXX')")
  (are-counts nb-events "?filter=(category='XXXXX') or (category='state')")
  (are-counts 0 "?filter=category='XXXXX' or category='YYYY'")
  (are-counts 0 "?filter=(category='XXXXX') or (category='YYYY')"))


(deftest filter-multiple
  (are-counts 0 "?filter=category='state'&filter=category='XXX'")
  (are-counts 1 "?filter=category='state'&filter=content/resource/href='run/3'"))


(deftest filter-nulls
  (are-counts nb-events "?filter=category!=null")
  (are-counts nb-events "?filter=null!=category")
  (are-counts 0 "?filter=category=null")
  (are-counts 0 "?filter=null=category")
  (are-counts nb-events "?filter=(unknown=null)and(category='state')")
  (are-counts nb-events "?filter=(content/resource/href!=null)and(category='state')"))


(deftest filter-prefix
  (are-counts nb-events "?filter=category^='st'")
  (are-counts nb-events "?filter=content/resource/href^='run/'")
  (are-counts 0 "?filter=category^='stXXX'")
  (are-counts 0 "?filter=content/resource/href^='XXX/'"))


(deftest filter-wrong-param
  (-> (exec-request base-uri "?filter=category='missing end quote" "user/joe")
      (ltu/is-status 400)
      (get-in [:response :body :message])
      (.startsWith "Invalid CIMI filter. Parse error at line 1, column 11")
      is))
