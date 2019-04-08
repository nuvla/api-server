(ns sixsq.nuvla.auth.utils)

(defn current-authentication
  "Extracts the current authentication from the ring request."
  [{:keys [nuvla/authn] :as request}]
  (select-keys authn [:user-id, :claims]))


(def ^{:doc "Internal administrator identity for database queries."}
internal-identity
  {:user-id "internal"
   :claims  #{"group/nuvla-admin" "group/nuvla-user" "group/nuvla-anon"}})
