(ns sixsq.nuvla.server.resources.service-attribute-test
  (:require
    [clojure.test :refer :all]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.service-attribute :refer :all]))


(deftest check-uri->id
  (are [arg] (re-matches #"^[\da-fA-F]+$" (uri->id arg))
             ""                                             ;; allowed here, not in Attribute schema
             "http://example.org/attributes"
             "http://example.org/attributes_with_accents_ôéå"
             "http://example.org/attributes#funky?query=/values"))

(deftest check-new-identifier
  (is (thrown? Exception (crud/new-identifier {:prefix " "} resource-type)))
  (is (thrown? Exception (crud/new-identifier {:prefix "http://example.org/invalid uri"} resource-type))))

(deftest check-valid-new-identifer
  (let [uri "example-org"
        name "price"
        hex (uri->id (str uri ":" name))
        id (str resource-type "/" hex)]
    (is (= id (:id (crud/new-identifier {:prefix uri :attributeName name} resource-type)))))

  (let [long-uri (apply str "http://" (repeat 10000 "a"))]
    (is (str resource-type "/" (uri->id long-uri)))))
