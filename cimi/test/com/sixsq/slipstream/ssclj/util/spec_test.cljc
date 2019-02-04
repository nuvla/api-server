(ns com.sixsq.slipstream.ssclj.util.spec-test
  (:require
    [clojure.spec.alpha :as s]
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.util.spec :as t]))

(deftest check-regex-chars
  (is (= 253 (count (@#'t/regex-chars #"."))))              ;; 3 characters are line terminators and not matched!
  (is (= #{"A"} (@#'t/regex-chars #"A")))
  (is (= #{"A" "B" "C" "0" "1" "-"} (@#'t/regex-chars #"[A-C0-1-]"))))

(deftest check-merge-kw-lists
  (is (= [:ns1/one :ns2/two] (t/merge-kw-lists [:ns2/two :ns1/one] [:ns1/one]))))

(deftest check-merge-keys-specs
  (let [test-keys-spec-1 {:req    [:ns1/one]
                          :req-un [:ns2/two]
                          :opt    [:ns3/three]}
        test-keys-spec-2 {:req [:ns5/five]}]
    (is (= {:req [:ns1/one]} (t/merge-keys-specs [{:req #{:ns1/one}} {:req [:ns1/one]}])))
    (is (= {:req    [:ns1/one :ns5/five]
            :req-un [:ns2/two]
            :opt    [:ns3/three]
            :opt-un [:ns4/four :ns6/six]}
           (t/merge-keys-specs [test-keys-spec-1
                                test-keys-spec-2
                                {:opt    [:ns3/three]
                                 :opt-un [:ns4/four]}
                                {:opt-un [:ns6/six]}])))))

(s/def :ns1/one boolean?)
(s/def :ns2/two string?)
(s/def :ns3/three keyword?)
(s/def :ns4/four pos-int?)
(s/def :spec.test/closed (t/only-keys :req [:ns1/one]
                                      :req-un [:ns2/two]
                                      :opt [:ns3/three]
                                      :opt-un [:ns4/four]))

(def keys-spec-req {:req [:ns1/one], :req-un [:ns2/two]})
(s/def :spec.test/also-closed (t/only-keys-maps keys-spec-req {:opt [:ns3/three], :opt-un [:ns4/four]}))

(s/def :spec.test/open (t/constrained-map keyword? pos-int? keys-spec-req {:opt [:ns3/three], :opt-un [:ns4/four]}))

(deftest check-spec-macros
  (let [valid-map {:ns1/one   true
                   :two       "OK"
                   :ns3/three :ok
                   :four      42}]
    (are [expect-fn arg] (expect-fn (s/valid? :spec.test/closed arg))
                         true? valid-map
                         false? (dissoc valid-map :ns1/one)
                         false? (dissoc valid-map :two)
                         true? (dissoc valid-map :ns3/three)
                         true? (dissoc valid-map :four)
                         false? (assoc valid-map :bad "BAD"))
    (are [expect-fn arg] (expect-fn (s/valid? :spec.test/also-closed arg))
                         true? valid-map
                         false? (dissoc valid-map :ns1/one)
                         false? (dissoc valid-map :two)
                         true? (dissoc valid-map :ns3/three)
                         true? (dissoc valid-map :four)
                         false? (assoc valid-map :bad "BAD"))
    (are [expect-fn arg] (expect-fn (s/valid? :spec.test/open arg))
                         true? valid-map
                         false? (dissoc valid-map :ns1/one)
                         false? (dissoc valid-map :two)
                         true? (dissoc valid-map :ns3/three)
                         true? (dissoc valid-map :four)
                         true? (assoc valid-map :other 10)
                         true? (assoc valid-map :other 1 :another 2)
                         false? (assoc valid-map :other -1)
                         false? (assoc valid-map :another "BAD"))))

