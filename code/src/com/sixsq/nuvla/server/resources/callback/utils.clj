(ns com.sixsq.nuvla.server.resources.callback.utils
  (:require
    [clojure.string :as str]
    [com.sixsq.nuvla.db.impl :as db]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.util.general :as gen-util]))


(defn executable?
  [{:keys [state expires tries-left] :or {tries-left 1}}]
  (and (= state "WAITING")
       (u/not-expired? expires)
       (pos? tries-left)))


(defn update-callback-state!
  [state callback-id]
  (try
    (-> (crud/retrieve-by-id-as-admin callback-id)
        (u/update-timestamps)
        (assoc :state state)
        db/edit)
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def callback-succeeded! (partial update-callback-state! "SUCCEEDED"))


(def callback-failed! (partial update-callback-state! "FAILED"))

(defn callback-dec-tries
  [callback-id]
  (try
    (-> (crud/retrieve-by-id-as-admin callback-id)
        (u/update-timestamps)
        (update :tries-left dec)
        db/edit)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defn callback-ui-url
  [callback-url]
  (let [base-uri (first (str/split callback-url #"api/callback"))]
    (str base-uri "ui/callback?callback-url=" (gen-util/encode-uri-component callback-url))))
