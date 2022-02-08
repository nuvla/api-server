(ns sixsq.nuvla.db.es.loader
  (:refer-clojure :exclude [load])
  (:require
    [sixsq.nuvla.db.es.binding :as esrb]
    [sixsq.nuvla.db.es.utils :as esru]))


(defn load
  "Creates an Elasticsearch client based on the Elasticsearch Java API. Takes
   the configuration parameters from the environmental variable ES_ENDPOINTS.
   These default to 'localhost:9200' if not specified. Also, conditionally
   creates Elasticsearch Sniffer."
  []
  (let [client  (esru/load-es-client)
        sniffer (esru/load-es-sniffer client)]
    (esrb/->ElasticsearchRestBinding client sniffer)))
