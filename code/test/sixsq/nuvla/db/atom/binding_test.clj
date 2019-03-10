(ns sixsq.nuvla.db.atom.binding-test
  (:require
    [clojure.test :refer [are deftest is]]
    [duratom.core :as duratom]
    [sixsq.nuvla.db.atom.binding :as t]
    [sixsq.nuvla.db.binding-lifecycle :as lifecycle]))

(deftest check-standard-atom
  (lifecycle/check-binding-lifecycle (t/->AtomBinding (atom {}))))

(deftest check-duratom
  (lifecycle/check-binding-lifecycle (t/->AtomBinding (duratom/duratom :local-file
                                                                       :file-path "target/duratom-db"
                                                                       :init {}))))
