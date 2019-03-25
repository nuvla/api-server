(ns sixsq.nuvla.db.es.binding
  "Binding protocol implemented for an Elasticsearch database that makes use
   of the Elasticsearch REST API."
  (:require
    [clojure.tools.logging :as log]
    [qbits.spandex :as spandex]
    [sixsq.nuvla.db.binding :refer [Binding]]
    [sixsq.nuvla.db.es.acl :as acl]
    [sixsq.nuvla.db.es.aggregation :as aggregation]
    [sixsq.nuvla.db.es.common.es-mapping :as mapping]
    [sixsq.nuvla.db.es.common.utils :as escu]
    [sixsq.nuvla.db.es.filter :as filter]
    [sixsq.nuvla.db.es.order :as order]
    [sixsq.nuvla.db.es.pagination :as paging]
    [sixsq.nuvla.db.es.select :as select]
    [sixsq.nuvla.db.utils.acl :as acl-utils]
    [sixsq.nuvla.db.utils.common :as cu]
    [sixsq.nuvla.server.util.response :as r])
  (:import
    (java.io Closeable)))

;; FIXME: Need to understand why the refresh parameter must be used to make unit test pass.

(defn create-client
  [options]
  (spandex/client options))


(defn create-index
  [client index]
  (try
    (let [{:keys [status]} (spandex/request client {:url [index], :method :head})]
      (if (= 200 status)
        (log/debug index "index already exists")
        (log/error "unexpected status code when checking" index "index (" status ")")))
    (catch Exception e
      (let [{:keys [status]} (ex-data e)]
        (try
          (if (= 404 status)
            (let [{{:keys [acknowledged shards_acknowledged]} :body} (spandex/request
                                                                       client
                                                                       {:url [index], :method :put})]
              (if (and acknowledged shards_acknowledged)
                (log/info index "index created")
                (log/warn index "index may or may not have been created")))
            (log/error "unexpected status code when checking" index "index (" status ")"))
          (catch Exception e
            (let [{:keys [status]} (ex-data e)]
              (log/error "unexpected status code when creating" index "index (" status ")"))))))))


(defn set-index-mapping
  [client index mapping]
  (try
    (let [{:keys [status]} (spandex/request client {:url    [index :_mapping :_doc]
                                                    :method :put
                                                    :body   mapping})]
      (if (= 200 status)
        (log/info index "mapping updated")
        (log/warn index "mapping could not be updated (" status ")")))
    (catch Exception e
      (let [{:keys [status]} (ex-data e)]
        (log/warn index "mapping could not be updated (" status ")")))))


(defn add-data
  [client {:keys [id] :as data}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index (escu/collection-id->index collection-id)
          response (spandex/request client {:url          [index :_doc uuid :_create]
                                            :query-string {:refresh true}
                                            :method       :put
                                            :body         (acl-utils/force-admin-role-right-all data)})
          success? (pos? (get-in response [:body :_shards :successful]))]
      (if success?
        (r/response-created id)
        (r/response-conflict id)))
    (catch Exception e
      (let [response (ex-data e)]
        (if (= 409 (-> response :body :status))
          (r/response-conflict id)
          (r/response-error (str "unexpected exception: " e)))))))


(defn update-data
  [client {:keys [id] :as data}]
  (let [[collection-id uuid] (cu/split-id id)
        index (escu/collection-id->index collection-id)
        updated-doc (acl-utils/force-admin-role-right-all data)
        response (spandex/request client {:url          [index :_doc uuid]
                                          :query-string {:refresh true}
                                          :method       :put
                                          :body         updated-doc})
        success? (pos? (get-in response [:body :_shards :successful]))]
    (if success?
      (r/json-response data)
      (r/response-conflict id))))


(defn find-data
  [client id]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index (escu/collection-id->index collection-id)
          response (spandex/request client {:url    [index :_doc uuid]
                                            :method :get})
          found? (get-in response [:body :found])]
      (if found?
        (-> response :body :_source)
        (throw (r/ex-not-found id))))
    (catch Exception e
      (let [response (ex-data e)
            status (:status response)]
        (if (= 404 status)
          (throw (r/ex-not-found id))
          (r/response-error (str "unexpected error retrieving " id)))))))


(defn delete-data
  [client id]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index (escu/collection-id->index collection-id)
          response (spandex/request client {:url          [index :_doc uuid]
                                            :query-string {:refresh true}
                                            :method       :delete})
          success? (pos? (get-in response [:body :_shards :successful]))
          deleted? (= "deleted" (get-in response [:body :result]))]
      (if (and success? deleted?)
        (r/response-deleted id)
        (r/response-error (str "could not delete document " id))))
    (catch Exception e
      (let [response (ex-data e)
            status (:status response)]
        (if (= 404 status)
          (throw (r/ex-not-found id))
          (r/response-error (str "unexpected error deleting " id)))))))


(defn query-data
  [client collection-id {:keys [cimi-params] :as options}]
  (let [index (escu/collection-id->index collection-id)
        paging (paging/paging cimi-params)
        orderby (order/sorters cimi-params)
        aggregation (aggregation/aggregators cimi-params)
        selected (select/select cimi-params)
        query {:query (acl/and-acl (filter/filter cimi-params) options)}
        body (merge paging orderby selected query aggregation)
        response (spandex/request client {:url    [index :_doc :_search]
                                          :method :post
                                          :body   body})
        success? (-> response :body :_shards :successful pos?)
        count-before-pagination (-> response :body :hits :total)
        aggregations (-> response :body :aggregations)
        meta (cond-> {:count count-before-pagination}
                     aggregations (assoc :aggregations aggregations))
        hits (->> response :body :hits :hits (map :_source))]
    (if success?
      [meta hits]
      (r/response-error "error when querying database"))))


(deftype ElasticsearchRestBinding [client]
  Binding

  (initialize [_ collection-id {:keys [spec] :as options}]
    (let [index (escu/collection-id->index collection-id)
          mapping (mapping/mapping spec)]
      (create-index client index)
      (set-index-mapping client index mapping)))


  (add [_ data options]
    (add-data client data))


  (add [_ collection-id data options]
    (add-data client data))


  (retrieve [_ id options]
    (find-data client id))


  (delete [_ {:keys [id]} options]
    (delete-data client id))


  (edit [_ data options]
    (update-data client data))


  (query [_ collection-id options]
    (query-data client collection-id options))


  Closeable
  (close [_]
    (spandex/close! client)))
