(ns sixsq.nuvla.auth.cookies
  "utilities for embedding and extracting tokens in cookies"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils.sign :as sg]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.group :as group]))


(defn revoked-cookie
  "Returns a cookie (as a map) that expires immediately and has 'INVALID' as
   the value. Useful for revoking a cookie in the client's cache. If a name
   is provided, then a map is returned with a single entry with the key being
   the name and the value being the cookie."
  ([]
   {:value "INVALID", :path "/", :max-age 0, :expires (ts/expiry-now-rfc822)})
  ([name]
   {name (revoked-cookie)}))


(defn create-cookie
  "Return a cookie (as a map) that has a token generated from the provided
   info and the default expiration time. If a name is provided, then a map
   is returned with a single entry with the key being the name and the value
   being the cookie."
  ([info]
   (let [timestamp   (ts/expiry-later)
         cookie-info (assoc info :exp timestamp)
         token       (sg/sign-cookie-info cookie-info)]
     {:value   token
      :secure  true
      :path    "/"
      :expires (ts/rfc822 timestamp)}))
  ([info name]
   {name (create-cookie info)}))

(defn extract-cookie-info
  "Extracts cookie info. Returns nil if no cookie is
   provided or if there is an error when extracting the value from the cookie."
  [{:keys [value] :as cookie}]
  (try
    (when value
      (sg/unsign-cookie-info value))
    (catch Exception e
      (log/warn "Error in extract-cookie-claims: " (str e))
      nil)))


(defn collect-groups-for-user
  [id]
  (->> (crud/query-as-admin
         group/resource-type
         {:cimi-params {:filter (parser/parse-cimi-filter (format "users='%s'" id))
                        :select ["id"]}})
       second
       (map :id)
       set))


(defn create-cookie-info
  [user-id & {:keys [session-id headers client-ip active-claim claims roles-ext]}]
  (let [server               (:nuvla-ssl-server-name headers)
        collected-groups-set (collect-groups-for-user user-id)
        groups               (-> collected-groups-set
                                 sort
                                 seq)]
    (cond-> {:user-id user-id
             :claims  (or (some->> claims seq sort (str/join " "))
                          (->> [user-id "group/nuvla-user" "group/nuvla-anon"]
                               (remove nil?)
                               sort
                               (str/join " ")))}
            groups (assoc :groups (str/join " " groups))
            roles-ext (update :claims #(str % " " (str/join " " roles-ext)))
            server (assoc :server server)
            session-id (assoc :session session-id)
            session-id (update :claims #(str % " " session-id))
            client-ip (assoc :client-ip client-ip)
            active-claim (assoc :active-claim active-claim))))
