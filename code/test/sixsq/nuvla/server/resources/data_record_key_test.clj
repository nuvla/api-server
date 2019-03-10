(ns sixsq.nuvla.server.resources.data-record-key-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.data-record-key :as data-record-key]))


(deftest check-uri->id
  (are [arg] (re-matches #"^[\da-fA-F]+$" (data-record-key/uri->id arg))
             ""                                             ;; allowed here, not in Attribute schema
             "http://example.org/attributes"
             "http://example.org/attributes_with_accents_ôéå"
             "http://example.org/attributes#funky?query=/values"))

(deftest check-new-identifier
  (is (thrown? Exception (crud/new-identifier {:prefix " "} data-record-key/resource-type)))
  (is (thrown? Exception (crud/new-identifier {:prefix "http://example.org/invalid uri"} data-record-key/resource-type))))

(deftest check-valid-new-identifer
  (let [uri "example-org"
        name "price"
        hex (data-record-key/uri->id (str uri ":" name))
        id (str data-record-key/resource-type "/" hex)]
    (is (= id (:id (crud/new-identifier {:prefix uri :key name} data-record-key/resource-type)))))

  (let [long-uri (apply str "http://" (repeat 10000 "a"))]
    (is (str data-record-key/resource-type "/" (data-record-key/uri->id long-uri)))))
