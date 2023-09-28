(ns sixsq.nuvla.db.es.binding
  "Binding protocol implemented for an Elasticsearch database that makes use
   of the Elasticsearch REST API."
  (:require [clojure.tools.logging :as log]
            [qbits.spandex :as spandex]
            [sixsq.nuvla.auth.utils.acl :as acl-utils]
            [sixsq.nuvla.db.binding :refer [Binding]]
            [sixsq.nuvla.db.es.acl :as acl]
            [sixsq.nuvla.db.es.aggregation :as aggregation]
            [sixsq.nuvla.db.es.common.es-mapping :as mapping]
            [sixsq.nuvla.db.es.common.utils :as escu]
            [sixsq.nuvla.db.es.filter :as filter]
            [sixsq.nuvla.db.es.order :as order]
            [sixsq.nuvla.db.es.pagination :as paging]
            [sixsq.nuvla.db.es.script-utils :refer [get-update-script]]
            [sixsq.nuvla.db.es.select :as select]
            [sixsq.nuvla.db.utils.common :as cu]
            [sixsq.nuvla.server.util.response :as r])
  (:import (java.io Closeable)))

;; FIXME: Need to understand why the refresh parameter must be used to make unit test pass.

(def ^:const sniff-interval-mills 5000)
(def ^:const sniff-after-failure-delay-mills 1000)


(defn create-client
  [options]
  (spandex/client options))


(defn create-sniffer
  [client options]
  (spandex/sniffer client (or options {})))

<<<<<<< Updated upstream

(defn create-index
  [client index]
  (try
    (let [{:keys [status]} (spandex/request client {:url [index], :method :head})]
      (if (= 200 status)
        (log/debug index "index already exists")
        (log/error "unexpected status code when checking" index "index (" status ")")))
    (catch Exception e
      (let [{:keys [status body]} (ex-data e)]
        (try
          (if (= 404 status)
            (let [{{:keys [acknowledged shards_acknowledged]} :body} (spandex/request
                                                                       client
                                                                       {:url [index], :method :put})]
              (if (and acknowledged shards_acknowledged)
                (log/info index "index created")
                (log/warn index "index may or may not have been created")))
            (log/error "unexpected status code when checking" index "index (" status "). " body))
          (catch Exception e
            (let [{:keys [status body] :as _response} (ex-data e)
                  error (:error body)]
              (log/error "unexpected status code when creating" index "index (" status "). " (or error e)))))))))


(defn set-index-mapping
  [client index mapping]
  (try
    (let [{:keys [body status]} (spandex/request client {:url    [index :_mapping]
                                                         :method :put
                                                         :body   mapping})]
      (if (= 200 status)
        (log/info index "mapping updated")
        (log/warn index "mapping could not be updated (" status "). " body)))
    (catch Exception e
      (let [{:keys [status body] :as _response} (ex-data e)
            error (:error body)]
        (log/warn index "mapping could not be updated (" status "). " (or error e))))))
=======
(defn get-error
  [ex]
  (let [response (ex-data ex)]
    (or (-> response r/get-body :error) ex)))
>>>>>>> Stashed changes


(defn document-missing?
  [ex]
  (-> (ex-data ex)
      r/get-body
      (get-in [:root_cause 0 :type])
      (= "document_missing_exception")))

(defn create-index
  [client index]
  (try
    (let [response (spandex/request client {:url [index], :method :head})]
      (if (r/status-ok? response)
        (log/debug index "index already exists")
        (log/error "unexpected status code when checking"
                   index "index (" (r/get-status response) ")")))
    (catch Exception e
      (let [response (ex-data e)
            body     (r/get-body response)
            status   (r/get-status response)]
        (try
          (if (r/status-not-found? response)
            (let [{:keys [acknowledged shards_acknowledged]}
                  (r/get-body (spandex/request
                                client
                                {:url [index], :method :put}))]
              (if (and acknowledged shards_acknowledged)
                (log/info index "index created")
                (log/warn index "index may or may not have been created")))
            (log/error "unexpected status code when checking" index "index (" status "). " body))
          (catch Exception e
            (log/error "unexpected status code when creating" index "index (" status "). "
                       (get-error e))))))))

(defn set-index-mapping
  [client index mapping]
  (try
    (let [response (spandex/request client {:url    [index :_mapping]
                                            :method :put
                                            :body   mapping})
          status   (r/get-status response)
          body     (r/get-body response)]
      (if (r/status-ok? response)
        (log/info index "mapping updated")
        (log/warn index "mapping could not be updated (" status "). " body)))
    (catch Exception e
      (let [response (ex-data e)]
        (log/warn index "mapping could not be updated ("
                  (r/get-status response) "). " (get-error e))))))

(defn add-data
  [client {:keys [id] :as data}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index       (escu/collection-id->index collection-id)
          updated-doc (-> data
                          (acl-utils/force-admin-role-right-all)
                          (acl-utils/normalize-acl-for-resource))
          response    (spandex/request client {:url          [index :_doc uuid :_create]
                                               :query-string {:refresh true}
                                               :method       :put
                                               :body         updated-doc})
<<<<<<< Updated upstream
          success?    (pos? (get-in response [:body :_shards :successful]))]
=======
          success?    (shards-successful? response)]
>>>>>>> Stashed changes
      (if success?
        (r/response-created id)
        (throw (r/ex-conflict id))))
    (catch Exception e
      (let [response (ex-data e)]
        (if (r/status-conflict? response)
          (throw (r/ex-conflict id))
          (throw (r/ex-unexpected (get-error e))))))))


(defn update-data
  [client {:keys [id] :as data}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index       (escu/collection-id->index collection-id)
          updated-doc (-> data
                          (acl-utils/force-admin-role-right-all)
                          (acl-utils/normalize-acl-for-resource))
          response    (spandex/request client {:url          [index :_doc uuid]
                                               :query-string {:refresh true}
                                               :method       :put
                                               :body         updated-doc})
<<<<<<< Updated upstream
          success?    (pos? (get-in response [:body :_shards :successful]))]
=======
          success?    (shards-successful? response)]
>>>>>>> Stashed changes
      (if success?
        (r/json-response data)
        (throw (r/ex-conflict id))))
    (catch Exception e
      (if (r/status-not-found? (ex-data e))
        (throw (r/ex-not-found id))
        (throw (r/ex-unexpected (get-error e)))))))

<<<<<<< Updated upstream
=======
(defn scripted-update-data
  [client id options]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index    (escu/collection-id->index collection-id)
          response (spandex/request client {:url          [index :_update uuid]
                                            :query-string {:refresh true}
                                            :method       :post
                                            :body         options})
          success? (shards-successful? response)]
      (if success?
        (r/response-updated id)
        (throw (r/ex-conflict id))))
    (catch Exception e
      (if (r/status-not-found? (ex-data e))
        (throw (r/ex-not-found id))
        (throw (r/ex-unexpected (get-error e)))))))
>>>>>>> Stashed changes

(defn find-data
  [client id]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index    (escu/collection-id->index collection-id)
          response (spandex/request client {:url    [index :_doc uuid]
                                            :method :get})
          body     (r/get-body response)
          found?   (:found body)]
      (if found?
        (:_source body)
        (throw (r/ex-not-found id))))
    (catch Exception e
      (if (r/status-not-found? (ex-data e))
        (throw (r/ex-not-found id))
        (throw (r/ex-unexpected (get-error e)))))))


(defn delete-data
  [client id]
  (let [[collection-id uuid] (cu/split-id id)
        index    (escu/collection-id->index collection-id)
        response (spandex/request client {:url          [index :_doc uuid]
                                          :query-string {:refresh true}
                                          :method       :delete})
<<<<<<< Updated upstream
        success? (pos? (get-in response [:body :_shards :successful]))
        deleted? (= "deleted" (get-in response [:body :result]))]
=======
        success? (shards-successful? response)
        deleted? (= "deleted" (-> response r/get-body :result))]
>>>>>>> Stashed changes
    (if (and success? deleted?)
      (r/response-deleted id)
      (throw (r/ex-unexpected (str "could not delete document " id))))))


(defn query-data
  [client collection-id {:keys [cimi-params] :as options}]
  (try
    (let [index                   (escu/collection-id->index collection-id)
          paging                  (paging/paging cimi-params)
          orderby                 (order/sorters cimi-params)
          aggregation             (aggregation/aggregators cimi-params)
          selected                (select/select cimi-params)
          query                   {:query (acl/and-acl-query (filter/filter cimi-params) options)}
          body                    (merge paging orderby selected query aggregation)
          response                (spandex/request client {:url    [index :_search]
                                                           :method :post
                                                           :body   body})
<<<<<<< Updated upstream
          success?                (-> response :body :_shards :successful pos?)
          count-before-pagination (-> response :body :hits :total :value)
          aggregations            (-> response :body :aggregations)
          meta                    (cond-> {:count count-before-pagination}
                                          aggregations (assoc :aggregations aggregations))
          hits                    (->> response :body :hits :hits (map :_source))]
      (if success?
=======
          body                    (r/get-body response)
          body-hits               (:hits body)
          count-before-pagination (-> body-hits :total :value)
          aggregations            (:aggregations body)
          meta                    (cond-> {:count count-before-pagination}
                                          aggregations (assoc :aggregations aggregations))
          hits                    (->> body-hits :hits (map :_source))]
      (if (shards-successful? response)
>>>>>>> Stashed changes
        [meta hits]
        (throw (r/ex-unexpected (str "error when querying: " body)))))
    (catch Exception e
      (throw (r/ex-unexpected (str "unexpected exception querying: " (get-error e)))))))


(defn bulk-edit-data
  [client collection-id
   {{:keys [doc]} :body
    :keys         [cimi-params operation] :as options}]
  (try
    (let [index         (escu/collection-id->index collection-id)
          body          {:query  (acl/and-acl-edit (filter/filter cimi-params) options)
                         :script (get-update-script doc operation)}
          response      (spandex/request client {:url          [index :_update_by_query]
                                                 :method       :post
                                                 :query-string {:refresh true}
                                                 :body         body})
          body-response (:body response)
          success?      (-> body-response :failures empty?)]
      (if success?
        body-response
        (throw (r/ex-unexpected (str "error when updating by query: " body-response)))))
    (catch Exception e
      (throw (r/ex-unexpected (str "error when updating by query: " (get-error e)))))))

(defn bulk-delete-data
  [client collection-id {:keys [cimi-params] :as options}]
  (try
    (let [index         (escu/collection-id->index collection-id)
          query         {:query (acl/and-acl-delete (filter/filter cimi-params) options)}
          response      (spandex/request client {:url          [index :_delete_by_query]
                                                 :query-string {:refresh true}
                                                 :method       :post
                                                 :body         query})
          body-response (:body response)
          success?      (-> body-response :failures empty?)]
      (if success?
        body-response
        (throw (r/ex-unexpected (str "error when deleting by query: " body-response)))))
    (catch Exception e
      (throw (r/ex-unexpected (str "error when deleting by query: " (get-error e)))))))


(deftype ElasticsearchRestBinding [client sniffer]
  Binding

  (initialize [_ collection-id {:keys [spec] :as _options}]
    (let [index   (escu/collection-id->index collection-id)
          mapping (mapping/mapping spec)]
      (create-index client index)
      (set-index-mapping client index mapping)))

  (add [_ data _options]
    (add-data client data))

  (add [_ _collection-id data _options]
    (add-data client data))

  (retrieve [_ id _options]
    (find-data client id))

  (delete [_ {:keys [id]} _options]
    (delete-data client id))

  (edit [_ data _options]
    (update-data client data))


  (query [_ collection-id options]
    (query-data client collection-id options))

  (bulk-delete [_ collection-id options]
    (bulk-delete-data client collection-id options))

  (bulk-edit [_ collection-id options]
    (bulk-edit-data client collection-id options))

  Closeable
  (close [_]
    (spandex/close! sniffer)
    (spandex/close! client)))
