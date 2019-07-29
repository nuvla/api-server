(ns sixsq.nuvla.db.atom.binding
  "Binding protocol implemented for data storage within an atom (or any object
   that behaves like an atom).

   The data is stored within the atom as a map with the following structure:

     {
       :collection-id-1 {
                          \"uuid1\" { ... document ... }
                          \"uuid2\" { ... document ... }
                        }
       :collection-id-2 {
                          \"uuid3\" { ... document ... }
                          \"uuid4\" { ... document ... }
                        }
     }

     The collection IDs are keywords, the document identifiers (uuids) are
     strings, and the document is standard EDN data."
  (:require
    [sixsq.nuvla.auth.utils.acl :as acl-utils]
    [sixsq.nuvla.db.binding :refer [Binding]]
    [sixsq.nuvla.db.utils.common :as cu]
    [sixsq.nuvla.server.util.response :as r])
  (:import
    (java.io Closeable)))


(defn atomic-retrieve [data-atom id]
  (when-let [path (cu/split-id-kw id)]
    (or (get-in @data-atom path)
        (throw (r/ex-not-found id)))))


(defn atomic-add
  [db {:keys [id] :as data}]
  (if-let [path (cu/split-id-kw id)]
    (if (get-in db path)
      (throw (r/ex-conflict id))
      (->> data
           acl-utils/force-admin-role-right-all
           acl-utils/normalize-acl-for-resource
           (assoc-in db path)))
    (throw (r/ex-bad-request "invalid document id"))))


(defn add-data [data-atom {:keys [id] :as data}]
  (try
    (swap! data-atom atomic-add data)
    (r/response-created id)
    (catch Exception e
      (ex-data e))))


(defn atomic-update
  [db {:keys [id] :as data}]
  (if-let [path (cu/split-id-kw id)]
    (if (get-in db path)
      (->> data
           acl-utils/force-admin-role-right-all
           acl-utils/normalize-acl-for-resource
           (assoc-in db path))
      (throw (r/ex-not-found id)))
    (throw (r/ex-bad-request "invalid document id"))))


(defn update-data [data-atom {:keys [id] :as data}]
  (try
    (swap! data-atom atomic-update data)
    (r/response-updated id)
    (catch Exception e
      (ex-data e))))


(defn atomic-delete
  [db {:keys [id] :as data}]
  (if-let [[collection-id doc-id :as path] (cu/split-id-kw id)]
    (if (get-in db path)
      (update-in db [collection-id] dissoc doc-id)
      (throw (r/ex-not-found id)))
    (throw (r/ex-bad-request "invalid document id"))))


(defn delete-data [data-atom {:keys [id] :as data}]
  (swap! data-atom atomic-delete data)
  (r/response-deleted id))


(defn query-info
  [data-atom collection-id options]
  (let [collection-kw (keyword collection-id)
        hits          (vals (collection-kw @data-atom))
        meta          {:count (count hits)}]
    [meta hits]))


(deftype AtomBinding
  [data-atom]

  Binding

  (initialize [_ collection-id options]
    nil)


  (add [_ data options]
    (add-data data-atom data))


  (add [_ _ data options]
    (add-data data-atom data))


  (retrieve [_ id options]
    (atomic-retrieve data-atom id))


  (delete [_ data options]
    (delete-data data-atom data))


  (edit [_ data options]
    (update-data data-atom data))


  (query [_ collection-id options]
    (query-info data-atom collection-id options))


  Closeable
  (close [_]
    nil))
