(ns sixsq.nuvla.server.resources.spec.common-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [sixsq.nuvla.server.resources.spec.common :as common]
    [sixsq.nuvla.server.resources.spec.core :as core]
    [sixsq.nuvla.server.resources.spec.spec-test-utils :as stu]
    [sixsq.nuvla.server.util.spec :as su]))

(deftest check-nonblank-string
  (doseq [v #{"ok" " ok" "ok " " ok "}]
    (stu/is-valid ::core/nonblank-string v))

  (doseq [v #{"" " " "\t" "\f" "\t\f"}]
    (stu/is-invalid ::core/nonblank-string v)))


(deftest check-timestamp
  (stu/is-valid ::core/timestamp "2012-01-01T01:23:45.678Z")
  (stu/is-invalid ::core/timestamp "2012-01-01T01:23:45.678Q"))


(deftest check-resource-link
  (doseq [v #{{:href "uri"}, {:href "uri" :ok "value"}}]
    (stu/is-valid ::core/resource-link v))

  (doseq [v #{{}, {:bad "value"}, {:href ""}}]
    (stu/is-invalid ::core/resource-link v)))


(deftest check-resource-links
  (stu/is-valid ::core/resource-links [{:href "uri"}])
  (stu/is-valid ::core/resource-links [{:href "uri"} {:href "uri"}])
  (stu/is-invalid ::core/resource-links []))


(deftest check-tags
  (doseq [v #{["ok"], ["a", "b"], ["ok", "also-ok"]}]
    (stu/is-valid ::common/tags v))

  (doseq [v #{{}, {1 "bad"}, {"bad" 1}, [:bad "bad"], {"a" "ok"}, {"a" "ok" "b" "ok"}, ["bad" "bad"]}]
    (stu/is-invalid ::common/tags v)))


(deftest check-acl
  (let [acl {:owners   ["group/nuvla-admin"]
             :view-acl ["group/nuvla-group1"]
             :edit-acl ["group/nuvla-group2"]}]

    (stu/is-valid ::common/acl acl)
    (stu/is-valid ::common/acl (dissoc acl :rules))

    (doseq [v #{{:rules []}, {:owner ""}, {:bad "BAD"}}]
      (stu/is-invalid ::common/acl (merge acl v)))))


(s/def ::common-attrs (su/only-keys-maps common/common-attrs))

(deftest check-common-attrs
  (let [date    "2012-01-01T01:23:45.678Z"
        acl     {:owners   ["group/nuvla-admin"]
                 :view-acl ["group/nuvla-group1"]
                 :edit-acl ["group/nuvla-group2"]}
        minimal {:id            "a"
                 :resource-type "http://example.org/data"
                 :created       date
                 :updated       date
                 :acl           acl}
        maximal (assoc minimal
                  :name "name"
                  :description "description"
                  :tags ["a"]
                  :operations [{:rel "add" :href "/add"}]
                  :acl acl)]

    (stu/is-valid ::common-attrs minimal)

    (stu/is-valid ::common-attrs maximal)

    (stu/is-invalid ::common-attrs (assoc maximal :bad "BAD"))

    (doseq [k #{:id :resource-type :created :updated}]
      (stu/is-invalid ::common-attrs (dissoc minimal k)))

    (doseq [k #{:name :description :tags}]
      (stu/is-valid ::common-attrs (dissoc maximal k)))))
