(ns com.sixsq.slipstream.ssclj.resources.session-oidc.utils-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.slipstream.ssclj.resources.session-oidc.utils :as t]))

(deftest check-prefix
  (are [expected args] (= expected (apply t/prefix args))
                       "a:b" ["a" "b"]
                       nil [nil "b"]
                       nil ["a" nil]
                       nil [nil nil]))

(deftest check-extract-roles
  (are [expected arg] (= expected (t/extract-roles arg))
                      [] nil
                      [] {}
                      [] {:realm "a"}
                      [] {:roles []}
                      [] {:roles [""]}
                      [] {:realm "a" :roles nil}
                      [] {:realm nil :roles ["a"]}
                      [] {:realm "" :roles ["a"]}
                      [] {:realm "a" :roles []}
                      [] {:realm "a" :roles [""]}
                      ["a:a"] {:realm "a" :roles ["a" nil ""]}
                      ["a:a" "a:b"] {:realm "a" :roles ["a" nil "b"]}))

(deftest check-extract-entitlements
  (are [expected arg] (= expected (t/extract-entitlements arg))
                      [] nil
                      [] {}
                      [] {:entitlement nil}
                      [] {:entitlement ""}
                      [] {:realm "a"}
                      [] {:entitlement []}
                      [] {:realm "a" :entitlement nil}
                      [] {:realm "a" :entitlement ""}
                      [] {:realm "a" :entitlement []}
                      [] {:realm "a" :entitlement [""]}
                      ["a:a"] {:realm "a" :entitlement "a"}
                      ["a:alpha"] {:realm "a" :entitlement "alpha"}
                      ["a:a"] {:realm "a" :entitlement ["a"]}
                      ["a:alpha"] {:realm "a" :entitlement ["alpha"]}
                      ["a:a" "a:b"] {:realm "a" :entitlement ["a" "b"]}
                      ["a:a" "a:b"] {:realm "a" :entitlement ["a" "b" nil ""]}
                      ["a:a" "a:b" "a:c"] {:realm "a" :entitlement ["a" "b" "c"]}
                      ["a:alpha" "a:beta"] {:realm "a" :entitlement ["alpha" "beta"]}
                      ["A:Alpha" "A:Beta"] {:realm "A" :entitlement ["Alpha" "Beta"]}))

(deftest check-group-hierarchy
  (are [expected arg] (= expected (t/group-hierarchy arg))
                      [] nil
                      [] ""
                      [] "/"
                      [] "//"
                      ["/a"] "a"
                      ["/a" "/a/b"] "/a/b"
                      ["/a" "/a/b"] "a//b"
                      ["/a" "/a/b" "/a/b/c"] "/a/b/c"))

(deftest check-extract-groups
  (are [expected arg] (= expected (t/extract-groups arg))
                      [] nil
                      [] {}
                      [] {:realm "a"}
                      [] {:groups []}
                      [] {:realm "a" :groups []}
                      ["a:/a"] {:realm "a" :groups ["/a"]}
                      ["a:/a" "a:/a/b"] {:realm "a" :groups ["/a/b"]}
                      ["a:/a" "a:/a/b" "a:/other"] {:realm "a" :groups ["/a/b" "/other"]}))
