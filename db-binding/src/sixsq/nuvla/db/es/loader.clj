(ns sixsq.nuvla.db.es.loader
  (:refer-clojure :exclude [load])
  (:require
    [sixsq.nuvla.db.es.binding :as esb]
    [sixsq.nuvla.db.es.utils :as esu]))


(defn load
  "Creates an Elasticsearch client based on the Elasticsearch Java API. Takes
   the configuration parameters from the environmental variables ES_HOST and
   ES_PORT. These default to 'localhost' and '9300' if not specified."
  []
  (-> (esu/create-es-client)
      esu/wait-for-cluster
      esb/->ESBindingLocal))
