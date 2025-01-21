(ns com.sixsq.nuvla.db.es.utils-test
  (:require [clojure.test :refer [deftest is]]
            [com.sixsq.nuvla.db.es.script-utils :refer [get-update-script]]))

(deftest create-update-script-for-tags
  (let [doc {:tags ["testing" "is" "good"]}]
    (is (= {:params {:tags ["testing" "is" "good"]},
            :source "ctx._source['tags']=params['tags']"}
           (get-update-script doc :set)
           ))
    (is (= {:params {:tags ["testing" "is" "good"]}, :source "if (ctx._source['tags']== null) ctx._source['tags']= new ArrayList();ctx._source['tags'].removeAll(params['tags']);ctx._source['tags'].addAll(params['tags'])"}
           (get-update-script doc :add)
           ))
    (is (= {:params {:tags ["testing" "is" "good"]},
            :source "if (ctx._source['tags']== null) ctx._source['tags']= new ArrayList();ctx._source['tags'].removeAll(params['tags'])"}
           (get-update-script doc :remove)))))

(deftest create-update-script-for-multiple-fields
  (let [doc {:tags  ["testing" "is" "good"]
             :other ["and" "fun"]}]
    (is (= {:params {:tags ["testing" "is" "good"], :other ["and" "fun"]}, :source "ctx._source['tags']=params['tags'];ctx._source['other']=params['other']"}
           (get-update-script doc :set)))
    (is (= {:params {:tags  ["testing" "is" "good"],
                     :other ["and" "fun"]},
            :source "if (ctx._source['tags']== null) ctx._source['tags']= new ArrayList();ctx._source['tags'].removeAll(params['tags']);ctx._source['tags'].addAll(params['tags']);if (ctx._source['other']== null) ctx._source['other']= new ArrayList();ctx._source['other'].removeAll(params['other']);ctx._source['other'].addAll(params['other'])"}
           (get-update-script doc :add)))
    (is (= {:params {:tags  ["testing" "is" "good"],
                     :other ["and" "fun"]},
            :source "if (ctx._source['tags']== null) ctx._source['tags']= new ArrayList();ctx._source['tags'].removeAll(params['tags']);if (ctx._source['other']== null) ctx._source['other']= new ArrayList();ctx._source['other'].removeAll(params['other'])"}
           (get-update-script doc :remove)))))