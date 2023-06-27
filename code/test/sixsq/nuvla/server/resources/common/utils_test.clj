(ns sixsq.nuvla.server.resources.common.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.util.time :as time]))


(deftest check-expired?-and-not-expired?
  (let [past-time   (time/to-str (time/ago 10 :minutes))
        future-time (time/to-str (time/from-now 10 :minutes))]
    (is (false? (u/expired? nil)))
    (is (true? (u/expired? past-time)))
    (is (false? (u/expired? future-time)))
    (is (true? (u/not-expired? nil)))
    (is (false? (u/not-expired? past-time)))
    (is (true? (u/not-expired? future-time)))))


(deftest check-select-desc-keys
  (let [resource {:name        "name"
                  :description "description"
                  :tags        #{"one", "two"}}]
    (is (= resource (u/select-desc-keys resource)))
    (is (= resource (u/select-desc-keys (assoc resource :other "ignored"))))
    (is (= (dissoc resource :name) (u/select-desc-keys (dissoc resource :name))))))


(deftest check-id-utils
  (are [expected id] (= expected (u/parse-id id))
                     nil nil
                     nil 47
                     ["" nil] ""
                     ["cloud-entry-point" nil] "cloud-entry-point"
                     ["resource" "uuid"] "resource/uuid"
                     ["resource" "uuid"] "resource/uuid/ignored")

  (are [expected id] (= expected (u/id->resource-type id))
                     nil nil
                     nil 47
                     "" ""
                     "cloud-entry-point" "cloud-entry-point"
                     "resource" "resource/uuid"
                     "resource" "resource/uuid/ignored")

  (are [expected id] (= expected (u/id->uuid id))
                     nil nil
                     nil 47
                     nil ""
                     nil "cloud-entry-point"
                     "uuid" "resource/uuid"
                     "uuid" "resource/uuid/ignored")

  (are [expected id] (= expected (u/id->request-params id))
                     nil nil
                     nil 47
                     {:resource-name "resource", :uuid "uuid"} "resource/uuid"
                     {:resource-name "cloud-entry-point"} "cloud-entry-point"))


(deftest uuid->short-uuid
  (is (= "39d6acef" (u/uuid->short-uuid "39d6acef-4693-46bc-a04f-85cd3cae79e"))))

(deftest create-generic-spec-validation-fn
  (let [f (u/create-generic-spec-validation-fn int? vector)]
    (is (= 1 (f 1)))
    (let [v "a"
          [v1 err-msg] (f v)]
      (is (= v v1))
      (is (= err-msg "-- Spec failed --------------------

  \"a\"

should satisfy

  int?

-------------------------
Detected 1 error
")))))

(deftest merge-resource
  (are [expect arg-map]
    (= expect (u/merge-resource
                (:resource arg-map)
                (:request arg-map)
                (:immutable-attributes arg-map)))
    {} {:resource {} :request {} :immutable-attributes []}
    {} {:resource {} :request {}}
    {} {:resource nil :request {}}
    {} {:resource nil :request nil}
    {:a 1} {:resource {:a 1} :request nil}
    {:a 1} {:resource {:a 1} :request {} :immutable-attributes [:a]}
    {:a 1} {:resource {:a 1} :request {:body {:a 2}} :immutable-attributes [:a]}
    {:a 1 :b 3} {:resource {:a 1 :b 2} :request {:body {:a 2 :b 3}} :immutable-attributes [:a]}
    {:a {:nested "something"} :b 3} {:resource {:a {:nested "something"} :b 2} :request {:body {:a 2 :b 3}} :immutable-attributes [:a]}
    {:a {:nested "something"} :b 2 :c 3} {:resource {:a {:nested "something"} :b 2 :c 3} :request {:body {:a 2 :b 3}} :immutable-attributes [:a :b]}))
