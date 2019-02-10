(ns sixsq.nuvla.db.es-rest.loader
  (:refer-clojure :exclude [load])
  (:require
    [environ.core :as env]
    [sixsq.nuvla.db.es-rest.binding :as esrb]))


(defn load
  "Creates an Elasticsearch client based on the Elasticsearch Java API. Takes
   the configuration parameters from the environmental variables ES_HOST and
   ES_PORT. These default to 'localhost' and '9200' if not specified."
  []
  (let [host (env/env :es-host "localhost")
        port (env/env :es-port "9200")
        hosts {:hosts [(str host ":" port)]}]
    (-> hosts
        esrb/create-client
        esrb/->ElasticsearchRestBinding)))
