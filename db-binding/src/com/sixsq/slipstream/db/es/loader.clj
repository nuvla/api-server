(ns com.sixsq.slipstream.db.es.loader
  (:refer-clojure :exclude [load])
  (:require
    [com.sixsq.slipstream.db.es.binding :as esb]
    [com.sixsq.slipstream.db.es.utils :as esu]))


(defn load
  "Creates an Elasticsearch client based on the Elasticsearch Java API. Takes
   the configuration parameters from the environmental variables ES_HOST and
   ES_PORT. These default to 'localhost' and '9300' if not specified."
  []
  (-> (esu/create-es-client) esu/wait-for-cluster esb/->ESBindingLocal))
