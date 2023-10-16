(ns sixsq.nuvla.server.util.kafka-crud
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.util.kafka :as ka]
    [taoensso.tufte :as tufte]))


(defn publish-on-add
  "Publish to a `topic` based on result of add response `add-response`."
  [topic add-response & {:keys [key] :or {key "resource-id"}}]
  (try
    (when (= 201 (:status add-response))
      (let [resource-id (-> add-response :body :resource-id)
            resource    (crud/retrieve-by-id-as-admin resource-id)
            msg-key     (if (= key "resource-id")
                          resource-id
                          ((keyword key) resource))]
        (log/debugf "publish on add: %s %s" msg-key resource)
        (ka/publish! topic msg-key resource)))
    (catch Exception e
      (log/warn "Failed publishing to Kafka on add: " (str e)))))


(defn publish-on-edit
  "Publish to a `topic` based on result of edit response `edit-response`."
  [topic edit-response & {:keys [key] :or {key "id"}}]
  (tufte/p "publish-on-edit"
    (try
     (when (= 200 (int (:status edit-response)))
       (let [msg-key  (-> edit-response :body (get (keyword key)))
             resource (:body edit-response)]
         (log/debugf "publish on edit: %s %s" msg-key resource)
         (ka/publish! topic msg-key resource)))
     (catch Exception e
       (log/warn "Failed publishing to Kafka on edit: " (str e))))))


(defn publish-tombstone
  "Publish tombstone message for `key` to `topic`."
  [topic key]
  (try
    (log/debugf "publish tombstone: %s" key)
    (ka/publish! topic key nil)
    (catch Exception e
      (log/warn "Failed publishing tombstone to Kafka: " (str e)))))
