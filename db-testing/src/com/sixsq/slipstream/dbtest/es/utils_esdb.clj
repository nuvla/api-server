(ns com.sixsq.slipstream.dbtest.es.utils-esdb
  (:require
    [com.sixsq.slipstream.db.es.binding :as esb]
    [com.sixsq.slipstream.db.es.utils :as esu]
    [com.sixsq.slipstream.db.impl :as db]
    [com.sixsq.slipstream.dbtest.es.utils :as esut]))

;;
;; DB related.
;;

(defmacro with-test-es-client-and-db-impl
  "Creates an Elasticsearch test client, executes the body with the created
   client bound to the Elasticsearch client binding, and then clean up the
   allocated resources by closing both the client and the node."
  [& body]
  `(with-open [node#   (esut/create-test-node)
               client# (-> node#
                           esu/node-client
                           esu/wait-for-cluster)]
     (db/set-impl! (esb/->ESBindingLocal client#))
     (esu/reset-index client# "_all")
     ~@body))

(defn test-fixture-es-client-and-db-impl
  [f]
  (with-test-es-client-and-db-impl
    (f)))
