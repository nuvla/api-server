(ns sixsq.nuvla.db.es.loader
  (:refer-clojure :exclude [load])
  (:require
    [sixsq.nuvla.db.es.binding :as esrb]
    [sixsq.nuvla.db.es.utils :as esru]))


(defn load
  "Creates an Elasticsearch client based on the Elasticsearch Java API. Takes
   the configuration parameters from the environmental variables ES_HOST and
   ES_PORT. These default to 'localhost' and '9200' if not specified."
  []
  (let [client (-> (esru/create-es-client)
                    esru/wait-for-cluster)
        ;; TODO: provide options to sniffer creation.
        sniffer (esrb/create-sniffer client {})]
    (esrb/->ElasticsearchRestBinding client sniffer)))
