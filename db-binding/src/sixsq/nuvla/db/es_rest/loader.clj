(ns sixsq.nuvla.db.es-rest.loader
  (:refer-clojure :exclude [load])
  (:require
    [environ.core :as env]
    [sixsq.nuvla.db.es-rest.binding :as esrb]
    [sixsq.nuvla.db.es-rest.utils :as esru]))


(defn load
  "Creates an Elasticsearch client based on the Elasticsearch Java API. Takes
   the configuration parameters from the environmental variables ES_HOST and
   ES_PORT. These default to 'localhost' and '9200' if not specified."
  []
  (-> (esru/create-es-client)
      esru/wait-for-cluster
      esrb/->ElasticsearchRestBinding))
