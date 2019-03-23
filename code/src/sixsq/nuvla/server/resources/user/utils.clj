(ns sixsq.nuvla.server.resources.user.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.credential-template :as credential-template]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as cthp]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-url "user")

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "ANON"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

(defn in?
  "true if coll contains elm."
  [coll elm]
  (if (some #(= elm %) coll) true false))


(defn admin?
  "Expects identity map from the request."
  [identity]
  (-> identity
      :authentications
      (get (:current identity))
      :roles
      (in? "ADMIN")))


(defn check-password-constraints
  [{:keys [password password-repeated]}]
  (cond
    (not (and password password-repeated)) (throw (r/ex-bad-request "both password fields must be specified"))
    (not= password password-repeated) (throw (r/ex-bad-request "password fields must be identical"))
    (not (hashed-password/acceptable-password? password)) (throw (r/ex-bad-request
                                                                   hashed-password/acceptable-password-msg)))
  true)


(defn user-id-identity
  [user-id]
  {:current         user-id,
   :authentications {user-id {:roles #{"USER"}, :identity user-id}}})


(defn create-hashed-password
  [user-id password]
  (let [request {:params   {:resource-name credential/resource-type}
                 :identity (user-id-identity user-id)
                 :body     {:template {:href              (str credential-template/resource-type
                                                               "/" cthp/method)
                                       :password          password
                                       :password-repeated password
                                       :parent            user-id}}}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      resource-id
      (throw (ex-info "" body)))))


(defn create-email
  [user-id email]
  (let [request {:params   {:resource-name email/resource-type}
                 :identity (user-id-identity user-id)
                 :body     {:parent  user-id
                            :address email}}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      resource-id
      (throw (ex-info "" body)))))


(defn create-identifier
  [user-id identifier]
  (let [request {:params   {:resource-name user-identifier/resource-type}
                 :identity std-crud/internal-identity
                 :body     {:parent     user-id
                            :identifier identifier}}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      resource-id
      (throw (ex-info (format "could not create identifier for '%s' -> '%s'" user-id identifier) body)))))


(defn update-user
  [user-id user-body]
  (let [request {:params   {:resource-name resource-url
                            :uuid          (second (str/split user-id #"/"))}
                 :identity std-crud/internal-identity
                 :body     user-body}
        {:keys [status body]} (crud/edit request)]
    (when (not= status 200)
      (throw (ex-info "" body)))))


(defn delete-user
  [user-id]
  (let [request {:params   {:resource-name resource-url
                            :uuid          (second (str/split user-id #"/"))}
                 :identity std-crud/internal-identity}
        {:keys [status body]} (crud/delete request)]
    (when (not= status 200)
      (throw (ex-info "" body)))))


(defn create-user-subresources
  [user-id email password username]
  (let [credential-id (create-hashed-password user-id password)
        email-id (some->> email
                          (create-email user-id))]

    (update-user user-id (cond-> {:id                  user-id
                                  :credential-password credential-id
                                  :acl                 {:owner {:principal "ADMIN"
                                                                :type      "ROLE"}
                                                        :rules [{:principal user-id
                                                                 :type      "USER"
                                                                 :right     "MODIFY"}]}}
                                 email-id (assoc :email email-id)))

    (when email
      (create-identifier user-id email))

    (when username
      (create-identifier user-id username))))

