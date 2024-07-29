(ns com.sixsq.nuvla.server.resources.data-record-key-test
  (:require
    [clojure.test :refer [are deftest is]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.data-record-key :as t]))


(deftest check-uri->id
  (are [arg] (re-matches #"^[\da-fA-F]+$" (t/uri->id arg))
             ""                                             ;; allowed here, not in Attribute schema
             "http://example.org/attributes"
             "http://example.org/attributes_with_accents_ôéå"
             "http://example.org/attributes#funky?query=/values"))

(deftest check-new-identifier
  (is (thrown? Exception (crud/new-identifier {:prefix " "} t/resource-type)))
  (is (thrown? Exception (crud/new-identifier {:prefix "http://example.org/invalid uri"} t/resource-type))))

(deftest check-valid-new-identifer
  (let [uri  "example-org"
        name "price"
        hex  (t/uri->id (str uri ":" name))
        id   (str t/resource-type "/" hex)]
    (is (= id (:id (crud/new-identifier {:prefix uri :key name} t/resource-type)))))

  (let [long-uri (apply str "http://" (repeat 10000 "a"))]
    (is (str t/resource-type "/" (t/uri->id long-uri)))))
