(ns sixsq.nuvla.server.resources.session-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.common.utils :as u]))

(deftest check-is-content-type?
  (are [expected-fn input] (expected-fn (u/is-content-type? input))
                           true? :content-type
                           true? "content-type"
                           true? "Content-Type"
                           true? "CONTENT-TYPE"
                           true? "CoNtEnT-TyPe"
                           false? 1234
                           false? nil))
