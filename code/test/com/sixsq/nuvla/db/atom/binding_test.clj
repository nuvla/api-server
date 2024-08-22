(ns com.sixsq.nuvla.db.atom.binding-test
  (:require
    [clojure.test :refer [deftest]]
    [com.sixsq.nuvla.db.atom.binding :as t]
    [com.sixsq.nuvla.db.binding-lifecycle :as lifecycle]
    [duratom.core :as duratom]))


(deftest check-standard-atom
  (lifecycle/check-binding-lifecycle (t/->AtomBinding (atom {}))))


(deftest check-duratom
  (lifecycle/check-binding-lifecycle (t/->AtomBinding (duratom/duratom :local-file
                                                                       :file-path "target/duratom-db"
                                                                       :init {}))))
