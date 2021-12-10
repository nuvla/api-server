(ns sixsq.nuvla.server.resources.spec.data-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.spec.data :as t]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]))


(def valid-polygons
  [; closed lon, lat
   [[[0 0] [0 100] [100 0] [0 0]]]
   ; closed lon, lat, alt
   [[[0 0 0] [0 100 0] [100 0 0] [0 0 0]]]
   ; closed polygon with a closed hole
   [[[0 0] [0 100] [100 0] [0 0]]
    [[10 10] [10 90] [90 10] [50 10] [10 10]]]])


(def valid-points
  [[0 0]
   [0 0 0]])


(def invalid-polygons
  [; three-points polygon
   [[[0 0] [0 100] [100 0]]]
   ; open polygon
   [[[0 0] [0 100] [100 0] [50 0]]]
   ; open and closed Polygon
   [[[0 0] [0 100] [100 0] [50 0]]
    [[0 0] [0 100] [100 0] [0 0]]]
   ; closed and open Polygon
   [[[0 0] [0 100] [100 0] [0 0]]
    [[0 0] [0 100] [100 0] [50 0]]]])


(def invalid-points
  [[0]
   [0 0 0 0]])


(deftest check-geometry
  (testing "Valid polygons"
    (doseq [p valid-polygons]
      (stu/is-valid ::t/geometry {:type "Polygon" :coordinates p})))

  (testing "Valid points"
    (doseq [p valid-points]
      (stu/is-valid ::t/geometry {:type "Point" :coordinates p})))

  (testing "Invalid polygons"
    (doseq [p (concat invalid-polygons valid-points)]
      (stu/is-invalid ::t/geometry {:type "Polygon" :coordinates p})))

  (testing "Invalid points"
    (doseq [p (concat invalid-points valid-polygons)]
      (stu/is-invalid ::t/geometry {:type "Point" :coordinates p}))))
