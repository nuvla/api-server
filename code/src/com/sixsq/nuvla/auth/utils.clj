(ns com.sixsq.nuvla.auth.utils
  (:require
    [clojure.string :as str]))

(def ^{:doc "Internal administrator identity for database queries."}
  internal-identity
  {:user-id      "internal"
   :active-claim "group/nuvla-admin"
   :claims       #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon"}})

(defn get-internal-request
  []
  {:nuvla/authn internal-identity})

(defn get-owner-authn
  [{:keys [owner] :as _resource}]
  {:claims       #{owner "group/nuvla-user" "group/nuvla-anon"}
   :user-id      owner
   :active-claim owner})

(defn get-owner-request
  [resource]
  {:nuvla/authn (get-owner-authn resource)})

(defn get-resource-id-authn
  [{id :id :as _resource}]
  {:claims       #{id "group/nuvla-user" "group/nuvla-anon"}
   :user-id      id
   :active-claim id})

(defn current-authentication
  "Extracts the current authentication from the ring request."
  [{:keys [nuvla/authn] :as _request}]
  (select-keys authn [:user-id :active-claim :claims]))

(defn current-user-id
  [request]
  (:user-id (current-authentication request)))

(defn current-active-claim
  [request]
  (:active-claim (current-authentication request)))

(defn current-session-id
  [request]
  (->> request
       current-authentication
       :claims
       (filter #(str/starts-with? % "session/"))
       first))

(defn request-as-user
  [request]
  (let [user-id      (current-user-id request)
        active-claim (current-active-claim request)]
    (if (and
          (str/starts-with? active-claim "group/")
          (not (#{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon"} active-claim)))
      (update request :nuvla/authn merge {:active-claim user-id
                                          :claims       #{user-id "group/nuvla-user" "group/nuvla-anon"}})
      request)))
