(ns sixsq.nuvla.dbtest.es.utils-esdb
  (:require
    [sixsq.nuvla.db.es.binding :as esb]
    [sixsq.nuvla.db.es.utils :as esu]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.dbtest.es.utils :as esut]))

;;
;; DB related.
;;

(defmacro with-test-es-client-and-db-impl
  "Creates an Elasticsearch test client, executes the body with the created
   client bound to the Elasticsearch client binding, and then clean up the
   allocated resources by closing both the client and the node."
  [& body]
  `(with-open [node# (esut/create-test-node)
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
