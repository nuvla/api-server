(ns com.sixsq.nuvla.migration.script.encrypt-credentials
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.core.async :as async :refer [go timeout chan take! go-loop buffer >! >!! <! <!!]]
            [com.sixsq.nuvla.migration.api-client :as api])
  (:import (java.util.concurrent Executors)))

(def pool (Executors/newFixedThreadPool 100))

(defonce creds (atom nil))

(def filter-secrets (str
                      "initialization-vector=null and ("
                      (str/join " or " (map #(str (name %) "!=null") [:secret :password :vpn-certificate :key :private-key :token :secret-key]))
                      ")"))

(defn fetch-all-creds-with-secrets
  []
  (reset! creds
          (loop [c      (->> (api/credentials
                               {:filter  filter-secrets
                                :last    10000
                                :orderby "created:asc"})
                             :resources)
                 result []]
            (if (seq (set/difference (set c) (set result)))
              (let [new-result (vec (distinct (concat result c)))]
                (recur (->> (api/credentials
                              {:filter  (str "created>='" (:created (last new-result)) "' and (" filter-secrets ")")
                               :last    10000
                               :orderby "created:asc"})
                            :resources)
                       new-result))
              result)))
  (println "Number of creds collected: " (count @creds)))

(defn print-number-of-secrets
  []
  (println "Number of creds found to be migrated: "
           (-> (api/credentials
                 {:filter      filter-secrets
                  :last        0
                  :aggregation "value_count:id"
                  :orderby     "created:asc"})
               (get-in [:aggregations :value_count:id :value]))))

(defn receive-n
  [c n]
  (loop [i   0
         res []]
    (when (< i n)
      (recur (inc i) (conj res (async/<!! c))))))

(defn edit-secrets-to-force-encrypt
  []
  (let [migrated-creds (atom 0)
        creds-count    (count @creds)
        c              (chan (buffer 1024))]
    (async/go-loop []
      (println "Migrated: " @migrated-creds "/" creds-count)
      (async/<! (async/timeout 5000))
      (when (< @migrated-creds creds-count)
        (recur)))
    (doall
      (pmap (fn [{:keys [id] :as cred}]
              (.submit pool (fn []
                              (>!! c (try
                                       (api/patch-resource id cred)
                                       id
                                       (catch Exception ex
                                         (println "Failed to update credential " id ": " ex)
                                         false)
                                       (finally
                                         (swap! migrated-creds inc)))))))
            @creds))
    (receive-n c creds-count)))

(comment
  (api/reset-session)
  (api/ensure-admin-session
    ;:dev-kb
    :preprod
    ;:prod
    )
  (print-number-of-secrets)
  (fetch-all-creds-with-secrets)
  (edit-secrets-to-force-encrypt))
