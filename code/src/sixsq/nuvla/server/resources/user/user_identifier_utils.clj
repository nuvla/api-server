(ns sixsq.nuvla.server.resources.user.user-identifier-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.auth.utils :as auth]))


(defn generate-identifier
  ([authn-method external-login]
   (generate-identifier authn-method external-login nil))
  ([authn-method external-login instance]
   (str (or instance (name authn-method)) ":" external-login)))


(defn add-user-identifier!
  [username authn-method external-login instance]
  (let [user-id    (str "user/" username)
        identifier (generate-identifier authn-method external-login instance)]
    (log/debugf "Creating a user-identifier resource for user %s with identifier %s" username identifier)
    (crud/add
      {:nuvla/authn  auth/internal-identity
       :params       {:resource-name user-identifier/resource-type}
       :route-params {:resource-name user-identifier/resource-type}
       :body         {:identifier identifier
                      :user       {:href user-id}}})))


(defn find-user-identifier
  "Returns the user-identifier resource associated with the given identifier.
   Returns nil if not found. Function never throws an exception."
  ([identifier]
   (try
     (->> identifier
          u/from-data-uuid
          (str user-identifier/resource-type "/")
          crud/retrieve-by-id-as-admin)
     (catch Exception _
       nil)))

  ([authn-method external-login & [instance]]
   (find-user-identifier (generate-identifier authn-method external-login instance))))


(defn user-identity-exists?
  [authn-method external-login & [instance]]
  (->> (generate-identifier authn-method external-login instance)
       find-user-identifier
       boolean))


(defn create-cimi-filter
  [filter]
  {:filter (parser/parse-cimi-filter filter)})


(defn ignore-deleted-user
  [user-id]
  (when-not (= "DELETED" (:state (crud/retrieve-by-id user-id)))
    user-id))


(defn user-identifier->user-id
  [authn-method instance external-login]
  (some-> (find-user-identifier authn-method external-login instance)
          :parent
          ignore-deleted-user
          (str/split #"/" 2)
          second))

