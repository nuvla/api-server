(ns com.sixsq.nuvla.server.resources.credential.key-utils-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer [deftest is]]
    [com.sixsq.nuvla.server.resources.credential.key-utils :as t]))


(deftest check-chars
  (is (= 56 (count t/secret-chars)))
  (is (= 56 (count t/secret-chars-set)))
  (is (= (set t/secret-chars) t/secret-chars-set)))


(deftest check-strip-invalid-chars
  (is (= "" (t/strip-invalid-chars "1lI0oO./-")))
  (is (= "YEA" (t/strip-invalid-chars "-Y1ElAI!."))))


(deftest test-digest-valid?-generate
  (is (= 2 (count (t/generate))))
  (is (apply t/valid? (t/generate)))
  (let [[secret digest] (t/generate)]
    (is (t/valid? secret digest))
    (is (t/valid? (str/replace secret #"\." "") digest))
    (is (t/valid? (str secret "1lI0oO./-") digest))
    (is (not (t/valid? (str secret "FAIL") digest)))))
