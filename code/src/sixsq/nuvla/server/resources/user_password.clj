(ns sixsq.nuvla.server.resources.user-password
  (:require
    [clojure.tools.logging :as log]
    [sixsq.nuvla.server.resources.callback :as callback]
    [sixsq.nuvla.server.resources.callback-user-email-validation :as user-email-callback]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.email.utils :as email-utils]
    [sixsq.nuvla.server.resources.spec.user]
    [sixsq.nuvla.server.resources.spec.user-template-password :as user-tpl-password]
    [sixsq.nuvla.server.resources.user :as p]
    [sixsq.nuvla.server.resources.user-template-password :as user-tpl]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.common.utils :as cu]))

;;
;; multimethods for validation
;;

(defn check-password-constraints
  [{:keys [password password-repeated]}]
  (cond
    (not (and password password-repeated)) (throw (r/ex-bad-request "both password fields must be specified"))
    (not= password password-repeated) (throw (r/ex-bad-request "password fields must be identical"))
    (not (hashed-password/acceptable-password? password)) (throw (r/ex-bad-request hashed-password/acceptable-password-msg)))
  true)


(def create-validate-fn (u/create-spec-validation-fn ::user-tpl-password/schema-create))


(defmethod p/create-validate-subtype user-tpl/registration-method
  [{resource :template :as create-document}]
  (check-password-constraints resource)
  (create-validate-fn create-document))


;;
;; transform template into user resource
;; strips method attribute and updates the resource URI
;;


(defn create-hashed-password
  [f user-record]
  (try
    (if-let [id "something"]
      (try
        (catch Exception e
          ;; delete id
          (throw e)))))
  nil)


(defn create-email
  [username email]
  nil)


(defn create-username-identity
  [username]
  nil)


(defn create-name-identity
  [username name]
  nil)


(defn create-email-identity
  [username email]
  nil)


(defn create-user-map
  [{:keys [name description tags email password] :as resource}]
  (let [username (cu/random-uuid)
        user-resource (cond-> {:resource-type p/resource-type
                               :isSuperUser   false
                               :deleted       false
                               :state         "NEW"
                               :username      username
                               :password      "invalid-password"
                               :email         email}
                              name (assoc :name name)
                              description (assoc :description description)
                              tags (assoc :tags tags))]
    
    (try
      (create-hashed-password username password)
      (create-email username email)
      (create-username-identity username)
      (create-name-identity username name)
      (create-email-identity username email)
      (catch Exception e
        (throw e)))
    user-resource))


(defmethod p/tpl->user user-tpl/registration-method
  [{:keys [redirectURI] :as resource} request]
  (let [user-map (create-user-map resource)]
    (if redirectURI
      [{:status 303, :headers {"Location" redirectURI}} user-map]
      [nil user-map])))


;;
;; creates email validation callback after user is created
;; logs and then ignores any exceptions when creating callback
;;

(def create-user-email-callback (partial callback/create user-email-callback/action-name))


(defmethod p/post-user-add user-tpl/registration-method
  [{:keys [id emailAddress] :as resource} {:keys [base-uri] :as request}]
  (try
    (-> (create-user-email-callback base-uri id)
        (email-utils/send-validation-email emailAddress))
    (catch Exception e
      (log/error (str e)))))
