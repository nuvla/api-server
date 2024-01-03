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
            [sixsq.nuvla.db.es.utils :as esu]
            [sixsq.nuvla.db.es.script-utils :refer [get-update-script]]
            [sixsq.nuvla.db.es.select :as select]
            [sixsq.nuvla.db.utils.common :as cu]
            [sixsq.nuvla.server.util.response :as r])
  (:import (java.io Closeable)))

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
            (let [{{:keys [acknowledged shards_acknowledged]} :body}
                  (spandex/request client {:url  [index], :method :put
                                           :body {:settings {:number_of_shards   3
                                                             :number_of_replicas 2}}})]
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

(defn shards-successful?
  [response]
  (pos? (get-in response [:body :_shards :successful])))

(defn noop?
  [response]
  (= (get-in response [:body :result]) "noop"))

(defn no-body-failures?
  [response]
  (empty? (get-in response [:body :failures])))

(defn add-data
  [client {:keys [id] :as data} {:keys [refresh]
                                 :or   {refresh true}
                                 :as   _options}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index       (escu/collection-id->index collection-id)
          updated-doc (-> data
                          (acl-utils/force-admin-role-right-all)
                          (acl-utils/normalize-acl-for-resource))
          response    (spandex/request client {:url          [index :_create uuid]
                                               :query-string {:refresh refresh}
                                               :method       :put
                                               :body         updated-doc})
          success?    (shards-successful? response)]
      (if success?
        (r/response-created id)
        (r/response-conflict id)))
    (catch Exception e
      (let [{:keys [status body] :as _response} (ex-data e)
            error (:error body)]
        (if (= 409 status)
          (r/response-conflict id)
          (r/response-error (str "unexpected exception: " (or error e))))))))

(defn update-data
  [client {:keys [id] :as data} {:keys [refresh]
                                 :or   {refresh true}
                                 :as   _options}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index       (escu/collection-id->index collection-id)
          updated-doc (-> data
                          (acl-utils/force-admin-role-right-all)
                          (acl-utils/normalize-acl-for-resource))
          response    (spandex/request client {:url          [index :_doc uuid]
                                               :query-string {:refresh refresh}
                                               :method       :put
                                               :body         updated-doc})
          success?    (shards-successful? response)]
      (if success?
        (r/json-response data)
        (r/response-conflict id)))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)]
        (r/response-error (str "unexpected exception updating " id ": " (or error e)))))))

(defn scripted-update-data
  [client id {:keys [body refresh retry_on_conflict]
              :or   {refresh           true
                     retry_on_conflict 3}
              :as   _options}]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index    (escu/collection-id->index collection-id)
          response (spandex/request client {:url          [index :_update uuid]
                                            :query-string {:refresh           refresh
                                                           :retry_on_conflict retry_on_conflict}
                                            :method       :post
                                            :body         body})
          success? (or (shards-successful? response)
                       (noop? response))]
      (if success?
        (r/map-response "updated successfully" 200 id)
        (r/response-conflict id)))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)]
        (r/response-error (str "unexpected exception scripted updating " id ": " (or error e)))))))

(defn find-data
  [client id]
  (try
    (let [[collection-id uuid] (cu/split-id id)
          index    (escu/collection-id->index collection-id)
          response (spandex/request client {:url    [index :_doc uuid]
                                            :method :get})
          found?   (get-in response [:body :found])]
      (if found?
        (-> response :body :_source)
        (throw (r/ex-not-found id))))
    (catch Exception e
      (let [{:keys [status] :as _response} (ex-data e)]
        (if (= 404 status)
          (throw (r/ex-not-found id))
          (throw e))))))

(defn delete-data
  [client id]
  (let [[collection-id uuid] (cu/split-id id)
        index    (escu/collection-id->index collection-id)
        response (spandex/request client {:url          [index :_doc uuid]
                                          :query-string {:refresh true}
                                          :method       :delete})
        success? (shards-successful? response)
        deleted? (= "deleted" (get-in response [:body :result]))]
    (if (and success? deleted?)
      (r/response-deleted id)
      (r/response-error (str "could not delete document " id)))))

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
          count-before-pagination (-> response :body :hits :total :value)
          aggregations            (-> response :body :aggregations)
          meta                    (cond-> {:count count-before-pagination}
                                          aggregations (assoc :aggregations aggregations))
          hits                    (->> response :body :hits :hits (map :_source))]
      (if (shards-successful? response)
        [meta hits]
        (let [msg (str "error when querying: " (:body response))]
          (throw (r/ex-response msg 500)))))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)
            msg   (str "unexpected exception querying: " (or error e))]
        (throw (r/ex-response msg 500))))))

(defn bulk-edit-data
  [client collection-id
   {{:keys [doc]} :body
    :keys         [cimi-params operation] :as options}]
  (try
    (let [index         (escu/collection-id->index collection-id)
          body          {:query  (acl/and-acl-edit (filter/filter cimi-params) options)
                         :script (get-update-script doc operation)}
          ;; refresh index to minimize risk of version conflicts
          _             (esu/refresh-index client index)
          response      (spandex/request client {:url          [index :_update_by_query]
                                                 :method       :post
                                                 :query-string {:refresh true}
                                                 :body         body})
          body-response (:body response)
          success?      (no-body-failures? response)]
      (if success?
        body-response
        (let [msg (str "error when updating by query: " body-response)]
          (throw (r/ex-response msg 500)))))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)
            msg   (str "unexpected exception updating by query: " _response (or error e))]
        (throw (r/ex-response msg 500))))))

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
        (let [msg (str "error when deleting by query: " body-response)]
          (throw (r/ex-response msg 500)))))
    (catch Exception e
      (let [{:keys [body] :as _response} (ex-data e)
            error (:error body)
            msg   (str "unexpected exception delete by query: " (or error e))]
        (throw (r/ex-response msg 500))))))


(deftype ElasticsearchRestBinding [client sniffer]
  Binding

  (initialize [_ collection-id {:keys [spec] :as _options}]
    (let [index   (escu/collection-id->index collection-id)
          mapping (mapping/mapping spec)]
      (create-index client index)
      (set-index-mapping client index mapping)))


  (add [_ data options]
    (add-data client data options))


  (retrieve [_ id _options]
    (find-data client id))


  (delete [_ {:keys [id]} _options]
    (delete-data client id))


  (edit [_ data options]
    (update-data client data options))

  (scripted-edit [_ id options]
    (scripted-update-data client id options))

  (query [_ collection-id options]
    (query-data client collection-id options))

  (bulk-delete [_ collection-id options]
    (bulk-delete-data client collection-id options))

  (bulk-edit [_ collection-id options]
    (bulk-edit-data client collection-id options))

  Closeable
  (close [_]
    (when sniffer
      (spandex/close! sniffer))
    (spandex/close! client)))
