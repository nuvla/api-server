(ns com.sixsq.slipstream.tools.cli.utils-test
  (:require
    [clojure.set :as set]
    [clojure.test :refer [are deftest is]]
    [clojure.tools.cli :as cli]
    [com.sixsq.slipstream.tools.cli.ssconfig :as ss]
    [com.sixsq.slipstream.tools.cli.utils :as u])
  (:import
    (java.util.regex Pattern)))


(deftest check-split-credentials
  (are [creds expected] (= (u/split-creds creds) expected)
                        nil [nil nil]
                        true [nil nil]
                        10 [nil nil]
                        "" ["" nil]
                        "user:pass" ["user" "pass"]))


(deftest test-modify-vals
  (let [resource {:ip    "1.2.3.4"
                  :url   "http://1.2.3.4/uri"
                  :dns   "https://example.com"
                  :multi {:level {:map "https://1.2.3.4/untouched"}}}
        modifiers [[#"1.2.3.4" "4.3.2.1"]
                   [#"example.com" "nuv.la"]]
        m (u/modify-vals resource modifiers)]
    (is (= m {:ip    "4.3.2.1"
              :url   "http://4.3.2.1/uri"
              :dns   "https://nuv.la"
              :multi {:level {:map "https://1.2.3.4/untouched"}}}))))


(deftest test-->config-resource
  (is (= "https://example.com/api/configuration/slipstream" (u/ss-cfg-url "https://example.com"))))


(deftest test-remove-attrs
  (is (= {} (u/remove-attrs {})))
  (is (= {:foo "bar"} (u/remove-attrs {:foo "bar"})))
  (is (= {:cloudServiceType "foo"} (u/remove-attrs {:cloudServiceType "foo"})))
  (is (nil? (:securityGroup (u/remove-attrs {:cloudServiceType "ec2" :securityGroup "secure"}))))
  (is (nil? (:pdiskEndpoint (u/remove-attrs {:cloudServiceType "nuvlabox" :pdiskEndpoint "endpoint"})))))


(deftest check-resource-type-from-str
  (are [expected arg] (= expected (u/resource-type-from-str arg))
                      nil nil
                      nil ""
                      nil "/uuid"
                      "my-type" "my-type/"
                      "my-type" "my-type/uuid"
                      "my-type" "my-type-template/"
                      "my-type" "my-type-template/uuid"))


(deftest check-resource-type
  (are [expected arg] (= expected (u/resource-type arg))
                      "my-type" "my-type/uuid"
                      "my-type" {:id "my-type/uuid"}
                      "my-type" {:body {:id "my-type/uuid"}}
                      nil {:body {}}))


(deftest test-->re-match-replace
  (let [[pattern replacement] (u/parse-replacement "a=b")]
    (is (= Pattern (type pattern)))
    (is (= "a" (str pattern)))
    (is (= "b" replacement))))


(deftest check-valid-options
  (let [args ["-e" "id=configuration/slipstream" "-e" "clientURL=https://159.100.242.202/downloads/slipstreamclient.tgz" "/etc/slipstream/slipstream.edn"]]
    (let [{:keys [:errors]} (cli/parse-opts args ss/cli-options)]
      (is (nil? errors)))))


(deftest check-parse-test
  (let [kvs [[:k :a] [:k :b] [:k :c] [:other :d]]]
    (is (= {:k #{:a :b :c} :other #{:d}} (reduce (fn [m [k v]] (u/cli-parse-sets m k v)) {} kvs)))))


(deftest check-mandatory-attrs
  ;;can only positive test cases where the provided map contains every mandatory keys
  ;; otherwise we would exit
  (is (= {:id "id"} (ss/check-mandatory-attrs {:id "id"})))
  (is (= {:id "id" :k "value"} (ss/check-mandatory-attrs {:id "id" :k "value"}))))