(ns sixsq.nuvla.server.resources.user.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.credential-template :as credential-template]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as cthp]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-url "user")


(defn check-password-constraints
  [{:keys [password]}]
  (cond
    (not (hashed-password/acceptable-password? password)) (throw (r/ex-bad-request
                                                                   hashed-password/acceptable-password-msg)))
  true)


(defn user-id-identity
  [user-id]
  {:user-id user-id,
   :claims  #{user-id "group/nuvla-user"}})


(defn create-hashed-password
  [user-id password]
  (let [request {:params      {:resource-name credential/resource-type}
                 :body        {:template {:href     (str credential-template/resource-type
                                                         "/" cthp/method)
                                          :password password
                                          :parent   user-id}}
                 :nuvla/authn (user-id-identity user-id)}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      resource-id
      (throw (ex-info "" body)))))


(defn create-email
  [user-id email & {:keys [validated], :or {validated false}}]
  (let [request {:params      {:resource-name email/resource-type}
                 :body        (cond-> {:parent  user-id
                                       :address email}
                                      validated (assoc :validated true))
                 :nuvla/authn (user-id-identity user-id)}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      resource-id
      (throw (ex-info "" body)))))


(defn create-identifier
  [user-id identifier]
  (let [request {:params      {:resource-name user-identifier/resource-type}
                 :body        {:parent     user-id
                               :identifier identifier}
                 :nuvla/authn auth/internal-identity}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (case status
      201 resource-id
      409 (throw (r/ex-response (format "Account with identifier \"%s\" already exist!" identifier) 409 identifier))
      (throw (ex-info (format "could not create identifier for '%s' -> '%s'" user-id identifier) body)))))


(defn update-user
  [user-id user-body]
  (let [request {:params      {:resource-name resource-url
                               :uuid          (second (str/split user-id #"/"))}
                 :body        user-body
                 :nuvla/authn auth/internal-identity}
        {:keys [status body]} (crud/edit request)]
    (when (not= status 200)
      (throw (ex-info "" body)))))


(defn delete-user
  [user-id]
  (let [request {:params      {:resource-name resource-url
                               :uuid          (second (str/split user-id #"/"))}
                 :nuvla/authn auth/internal-identity}
        {:keys [status body]} (crud/delete request)]
    (when (not= status 200)
      (throw (ex-info "" body)))))


(defn create-user-subresources
  [user-id email password username customer]

  (when email
    (create-identifier user-id email))

  (when username
    (create-identifier user-id username))

  (let [credential-id (when password (create-hashed-password user-id password))
        email-id      (when email (create-email user-id email))]

    (update-user user-id (cond-> {:id user-id}
                                 credential-id (assoc :credential-password credential-id)
                                 email-id (assoc :email email-id))))

  (when customer

    )
  )
