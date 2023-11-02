(ns sixsq.nuvla.db.es.filter-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.db.es.filter :as t]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.filter.parser-test :as parser-test]
    [sixsq.nuvla.server.util.time :as time])
  (:import (clojure.lang ExceptionInfo)))

(deftest check-parse-cimi-filter-base-case
  (is (= {:constant_score {:boost  1.0
                           :filter {:term {"id" "1"}}}}
         (t/transform (parser/parse-cimi-filter "id='1'")))))

(def wkt-point "POINT (30 10)")
(def wkt-simple-polygon "POLYGON ((30 10, 40 40, 20 40, 10 20, 30 10))")
(def wkt-complex-polygon "POLYGON ((35 10, 45 45, 15 40, 10 20, 35 10), (20 30, 35 35, 30 20, 20 30))")
(def wkt-multi-polygon (str "MULTIPOLYGON (((40 40, 20 45, 45 30, 40 40)), "
                            "((20 35, 10 30, 10 10, 30 5, 45 20, 20 35), (30 20, 20 15, 20 25, 30 20)))"))

(def geojson-point {:coordinates [30.0 10.0]
                    :type        "Point"})
(def geojson-simple-polygon {:coordinates [[[30.0 10.0]
                                            [40.0 40.0]
                                            [20.0 40.0]
                                            [10.0 20.0]
                                            [30.0 10.0]]]
                             :type        "Polygon"})
(def geojson-complex-polygon {:coordinates [[[35.0 10.0]
                                             [45.0 45.0]
                                             [15.0 40.0]
                                             [10.0 20.0]
                                             [35.0 10.0]]
                                            [[20.0 30.0]
                                             [35.0 35.0]
                                             [30.0 20.0]
                                             [20.0 30.0]]]
                              :type        "Polygon"})

(def geojson-multi-polygon {:coordinates [[[[40.0 40.0]
                                            [20.0 45.0]
                                            [45.0 30.0]
                                            [40.0 40.0]]]
                                          [[[20.0 35.0]
                                            [10.0 30.0]
                                            [10.0 10.0]
                                            [30.0 5.0]
                                            [45.0 20.0]
                                            [20.0 35.0]]
                                           [[30.0 20.0]
                                            [20.0 15.0]
                                            [20.0 25.0]
                                            [30.0 20.0]]]]
                            :type        "MultiPolygon"})

(deftest check-valid-parse-wkt
  (are [expected v] (= expected (t/parse-wkt v))
                    geojson-point
                    wkt-point

                    geojson-simple-polygon
                    wkt-simple-polygon

                    geojson-complex-polygon
                    wkt-complex-polygon

                    geojson-multi-polygon
                    wkt-multi-polygon
                    ))


(deftest check-invalid-parse-wkt
  (is (thrown-with-msg? ExceptionInfo #"invalid WKT format 'POINT \(30 10t\)'\. Invalid number: 10t \(line 1\)\." (t/parse-wkt "POINT (30 10t)")))
  (is (thrown-with-msg? ExceptionInfo #"invalid WKT format.*" (t/parse-wkt "")))
  (is (thrown-with-msg? ExceptionInfo #"invalid WKT format.*" (t/parse-wkt nil))))


(deftest check-geo-filter
  (are [expected filter-str]
    (= expected (t/filter {:filter (parser/parse-cimi-filter filter-str)}))

    {:constant_score {:boost  1.0
                      :filter {:geo_shape {"attr" {:relation "intersects"
                                                   :shape    geojson-point}}}}}
    (format "attr intersects '%s'" wkt-point)

    {:constant_score {:boost  1.0
                      :filter {:geo_shape {"attr" {:relation "within"
                                                   :shape    geojson-simple-polygon}}}}}
    (format "attr within '%s'" wkt-simple-polygon)

    {:constant_score {:boost  1.0
                      :filter {:geo_shape {"attr" {:relation "disjoint"
                                                   :shape    geojson-complex-polygon}}}}}
    (format "attr disjoint '%s'" wkt-complex-polygon)

    {:constant_score {:boost  1.0
                      :filter {:geo_shape {"attr2" {:relation "contains"
                                                    :shape    geojson-multi-polygon}}}}}
    (format "attr2 contains '%s'" wkt-multi-polygon)))

(deftest check-transform-or-query
  (is (= {:constant_score {:boost  1.0
                           :filter {:bool {:should [{:bool {:must_not {:exists {:field "id"}}}}
                                                    {:term {"b" true}}
                                                    {:term {"c" 1}}
                                                    {:term {"d" "foo"}}]}}}}
         (t/transform parser-test/expected-transform-or-query))))

(deftest check-transform-and-query
  (is (= {:constant_score {:boost  1.0
                           :filter {:bool {:filter [{:bool {:must_not {:exists {:field "id"}}}}
                                                    {:term {"b" true}}
                                                    {:term {"c" 1}}]}}}}
         (t/transform parser-test/expected-transform-and-query))))

(deftest check-transform-values-query
  (is (= {:constant_score {:boost  1.0
                           :filter {:terms {"id" ["a" "b" 1]}}}}
         (t/transform parser-test/expected-transform-values-query))))

(deftest check-transform-complex-query
  (is (= {:constant_score
          {:boost  1.0
           :filter {:bool
                    {:should
                     [{:bool
                       {:filter
                        [{:bool
                          {:should [{:bool {:must_not {:exists {:field "id"}}}}
                                    {:term {"id" "x"}}
                                    {:bool {:filter [{:term {"d" 1}}
                                                     {:term {"b" true}}]}}]}}
                         {:bool {:must_not {:term {"name" "s"}}}}
                         {:term {"acl.owner" "user/1"}}
                         {:geo_shape {"attr" {:relation "intersects"
                                              :shape    {:coordinates [1.0
                                                                       2.0]
                                                         :type        "Point"}}}}]}}
                      {:range {"created" {:gte (time/date-from-str
                                                 "2022-03-29T14:21:37.659Z")}}}]}}}}
         (t/transform parser-test/expected-transform-complex-query))))
