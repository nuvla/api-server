(ns com.sixsq.nuvla.server.resources.mec.query-filter-test
  "Tests for MEC 010-2 Query Filter Parser"
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.sixsq.nuvla.server.resources.mec.query-filter :as qf]))


;;
;; Test Data
;;

(def test-resources
  [{:id "app-1" :appName "web-app" :operationalState "STARTED" :cpu 2 :memory 4096}
   {:id "app-2" :appName "api-service" :operationalState "STOPPED" :cpu 4 :memory 8192}
   {:id "app-3" :appName "web-app" :operationalState "STARTED" :cpu 1 :memory 2048}
   {:id "app-4" :appName "database" :operationalState "STARTED" :cpu 8 :memory 16384}
   {:id "app-5" :appName "cache" :operationalState "STOPPED" :cpu 2 :memory 4096}])


;;
;; Filter Parsing Tests
;;

(deftest test-parse-filter-eq
  (testing "Parse equality filter"
    (let [filter-expr (qf/parse-filter "(eq,appName,web-app)")]
      (is (= :eq (:op filter-expr)))
      (is (= :appName (:field filter-expr)))
      (is (= "web-app" (:value filter-expr))))))


(deftest test-parse-filter-neq
  (testing "Parse not-equal filter"
    (let [filter-expr (qf/parse-filter "(neq,operationalState,STOPPED)")]
      (is (= :neq (:op filter-expr)))
      (is (= :operationalState (:field filter-expr)))
      (is (= "STOPPED" (:value filter-expr))))))


(deftest test-parse-filter-gt
  (testing "Parse greater-than filter"
    (let [filter-expr (qf/parse-filter "(gt,cpu,2)")]
      (is (= :gt (:op filter-expr)))
      (is (= :cpu (:field filter-expr)))
      (is (= "2" (:value filter-expr))))))


(deftest test-parse-filter-lt
  (testing "Parse less-than filter"
    (let [filter-expr (qf/parse-filter "(lt,memory,8192)")]
      (is (= :lt (:op filter-expr)))
      (is (= :memory (:field filter-expr)))
      (is (= "8192" (:value filter-expr))))))


(deftest test-parse-filter-in
  (testing "Parse in-set filter"
    (let [filter-expr (qf/parse-filter "(in,appName,web-app,api-service,database)")]
      (is (= :in (:op filter-expr)))
      (is (= :appName (:field filter-expr)))
      (is (= ["web-app" "api-service" "database"] (:values filter-expr))))))


(deftest test-parse-filter-and
  (testing "Parse AND filter"
    (let [filter-expr (qf/parse-filter "(and,(eq,appName,web-app),(eq,operationalState,STARTED))")]
      (is (= :and (:op filter-expr)))
      (is (= 2 (count (:exprs filter-expr))))
      (is (= :eq (:op (first (:exprs filter-expr)))))
      (is (= :eq (:op (second (:exprs filter-expr))))))))


(deftest test-parse-filter-or
  (testing "Parse OR filter"
    (let [filter-expr (qf/parse-filter "(or,(eq,appName,web-app),(eq,appName,database))")]
      (is (= :or (:op filter-expr)))
      (is (= 2 (count (:exprs filter-expr)))))))


(deftest test-parse-filter-empty
  (testing "Parse empty filter returns nil"
    (is (nil? (qf/parse-filter "")))
    (is (nil? (qf/parse-filter nil)))
    (is (nil? (qf/parse-filter "   ")))))


;;
;; Filter Application Tests
;;

(deftest test-apply-filter-eq
  (testing "Apply equality filter"
    (let [filter-expr (qf/parse-filter "(eq,appName,web-app)")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 2 (count result)))
      (is (every? #(= "web-app" (:appName %)) result)))))


(deftest test-apply-filter-neq
  (testing "Apply not-equal filter"
    (let [filter-expr (qf/parse-filter "(neq,operationalState,STOPPED)")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 3 (count result)))
      (is (every? #(not= "STOPPED" (:operationalState %)) result)))))


(deftest test-apply-filter-gt
  (testing "Apply greater-than filter"
    (let [filter-expr (qf/parse-filter "(gt,cpu,2)")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 2 (count result)))
      (is (every? #(> (:cpu %) 2) result)))))


(deftest test-apply-filter-lt
  (testing "Apply less-than filter"
    (let [filter-expr (qf/parse-filter "(lt,memory,8192)")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 3 (count result)))
      (is (every? #(< (:memory %) 8192) result)))))


(deftest test-apply-filter-in
  (testing "Apply in-set filter"
    (let [filter-expr (qf/parse-filter "(in,appName,web-app,database)")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 3 (count result)))
      (is (every? #(contains? #{"web-app" "database"} (:appName %)) result)))))


(deftest test-apply-filter-and
  (testing "Apply AND filter"
    (let [filter-expr (qf/parse-filter "(and,(eq,appName,web-app),(eq,operationalState,STARTED))")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 2 (count result)))
      (is (every? #(and (= "web-app" (:appName %))
                        (= "STARTED" (:operationalState %)))
                  result)))))


(deftest test-apply-filter-or
  (testing "Apply OR filter"
    (let [filter-expr (qf/parse-filter "(or,(eq,appName,web-app),(eq,appName,database))")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 3 (count result)))
      (is (every? #(or (= "web-app" (:appName %))
                       (= "database" (:appName %)))
                  result)))))


(deftest test-apply-filter-complex
  (testing "Apply complex nested filter"
    (let [filter-expr (qf/parse-filter "(and,(or,(eq,appName,web-app),(eq,appName,database)),(eq,operationalState,STARTED))")
          result (qf/apply-filter filter-expr test-resources)]
      (is (= 3 (count result)))
      (is (every? #(= "STARTED" (:operationalState %)) result)))))


(deftest test-apply-filter-nil
  (testing "Apply nil filter returns all resources"
    (let [result (qf/apply-filter nil test-resources)]
      (is (= (count test-resources) (count result))))))


;;
;; Pagination Tests
;;

(deftest test-paginate-first-page
  (testing "Paginate first page"
    (let [result (qf/paginate test-resources {:page 1 :size 2 :base-uri "/app_instances"})]
      (is (= 2 (count (:items result))))
      (is (= 5 (:total result)))
      (is (= 1 (:page result)))
      (is (= 2 (:size result)))
      (is (= 3 (:totalPages result)))
      (is (some? (get-in result [:_links :self])))
      (is (some? (get-in result [:_links :next])))
      (is (nil? (get-in result [:_links :prev]))))))


(deftest test-paginate-middle-page
  (testing "Paginate middle page"
    (let [result (qf/paginate test-resources {:page 2 :size 2 :base-uri "/app_instances"})]
      (is (= 2 (count (:items result))))
      (is (= 2 (:page result)))
      (is (some? (get-in result [:_links :prev])))
      (is (some? (get-in result [:_links :next])))
      (is (some? (get-in result [:_links :first]))))))


(deftest test-paginate-last-page
  (testing "Paginate last page"
    (let [result (qf/paginate test-resources {:page 3 :size 2 :base-uri "/app_instances"})]
      (is (= 1 (count (:items result))))
      (is (= 3 (:page result)))
      (is (some? (get-in result [:_links :prev])))
      (is (nil? (get-in result [:_links :next]))))))


(deftest test-paginate-size-cap
  (testing "Paginate size capped at 100"
    (let [result (qf/paginate test-resources {:page 1 :size 200})]
      (is (<= (:size result) 100)))))


(deftest test-paginate-invalid-page
  (testing "Paginate invalid page number defaults to 1"
    (let [result (qf/paginate test-resources {:page 0 :size 2})]
      (is (= 1 (:page result))))))


;;
;; Field Selection Tests
;;

(deftest test-parse-fields
  (testing "Parse fields parameter"
    (let [fields (qf/parse-fields "appName,operationalState,cpu")]
      (is (= #{:appName :operationalState :cpu} fields)))))


(deftest test-parse-fields-empty
  (testing "Parse empty fields returns nil"
    (is (nil? (qf/parse-fields "")))
    (is (nil? (qf/parse-fields nil)))
    (is (nil? (qf/parse-fields "   ")))))


(deftest test-select-fields
  (testing "Select specific fields from resources"
    (let [fields #{:appName :operationalState}
          result (qf/select-fields fields test-resources)]
      (is (= 5 (count result)))
      (is (every? #(contains? % :id) result)) ; :id always included
      (is (every? #(contains? % :appName) result))
      (is (every? #(contains? % :operationalState) result))
      (is (every? #(not (contains? % :cpu)) result))
      (is (every? #(not (contains? % :memory)) result)))))


(deftest test-select-fields-nil
  (testing "Select nil fields returns all fields"
    (let [result (qf/select-fields nil test-resources)]
      (is (= (count test-resources) (count result)))
      (is (every? #(contains? % :cpu) result)))))


;;
;; Combined Query Processing Tests
;;

(deftest test-process-query-filter-only
  (testing "Process query with filter only"
    (let [result (qf/process-query test-resources {:filter "(eq,appName,web-app)"})]
      (is (= 2 (count (:items result))))
      (is (= 2 (:total result))))))


(deftest test-process-query-pagination-only
  (testing "Process query with pagination only"
    (let [result (qf/process-query test-resources {:page "1" :size "2"})]
      (is (= 2 (count (:items result))))
      (is (= 5 (:total result)))
      (is (= 1 (:page result))))))


(deftest test-process-query-fields-only
  (testing "Process query with field selection only"
    (let [result (qf/process-query test-resources {:fields "appName,operationalState"})]
      (is (every? #(contains? % :id) (:items result)))
      (is (every? #(contains? % :appName) (:items result)))
      (is (every? #(not (contains? % :cpu)) (:items result))))))


(deftest test-process-query-combined
  (testing "Process query with filter, pagination, and field selection"
    (let [result (qf/process-query test-resources
                                   {:filter "(eq,operationalState,STARTED)"
                                    :page "1"
                                    :size "2"
                                    :fields "appName,cpu"
                                    :base-uri "/app_instances"})]
      (is (= 2 (count (:items result))))
      (is (= 3 (:total result))) ; 3 STARTED apps total
      (is (every? #(contains? % :appName) (:items result)))
      (is (every? #(not (contains? % :memory)) (:items result)))
      (is (some? (get-in result [:_links :self])))
      (is (some? (get-in result [:_links :next]))))))


(deftest test-process-query-empty
  (testing "Process query with no parameters"
    (let [result (qf/process-query test-resources {})]
      (is (= 5 (count (:items result))))
      (is (= 5 (:total result)))
      (is (= 1 (:page result)))
      (is (= 20 (:size result))))))


;;
;; Module Completeness Test
;;

(deftest test-query-filter-complete
  (testing "Verify all expected functions are available"
    (let [expected-fns ['parse-filter
                        'apply-filter
                        'paginate
                        'parse-fields
                        'select-fields
                        'process-query]]
      (doseq [fn-name expected-fns]
        (is (some? (ns-resolve 'com.sixsq.nuvla.server.resources.mec.query-filter fn-name))
            (str "Function " fn-name " should be defined"))))))
