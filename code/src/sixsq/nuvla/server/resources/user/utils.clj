(ns sixsq.nuvla.server.resources.user.utils
  (:require
    [clojure.string :as str]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.callback.email-utils :as callback-email-utils]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-hashed-password :as hashed-password]
    [sixsq.nuvla.server.resources.credential-template :as credential-template]
    [sixsq.nuvla.server.resources.credential-template-hashed-password :as cthp]
    [sixsq.nuvla.server.resources.credential-template-totp-2fa :as cttotp]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const resource-url "user")

(def ^:const customer-resource-url "customer")


(defn check-password-constraints
  [{:keys [password]}]
  (cond
    (not (hashed-password/acceptable-password? password)) (throw (r/ex-bad-request
                                                                   hashed-password/acceptable-password-msg)))
  true)


(defn user-id-identity
  [user-id]
  {:user-id      user-id
   :active-claim user-id
   :claims       #{user-id "group/nuvla-user"}})


(defn create-credential
  [body authn]
  (let [request {:params      {:resource-name credential/resource-type}
                 :body        body
                 :nuvla/authn authn}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      resource-id
      (throw (ex-info "" body)))))

(defn create-hashed-password
  [user-id password]
  (create-credential
    {:template {:href     (str credential-template/resource-type
                               "/" cthp/method)
                :password password
                :parent   user-id}}
    (user-id-identity user-id)))


(defn create-totp-credential
  [user-id secret]
  (create-credential
    {:template {:href   (str credential-template/resource-type
                             "/" cttotp/method)
                :secret secret
                :parent user-id}}
    auth/internal-identity))


(defn create-email
  [user-id email & {:keys [validated], :or {validated false}}]
  (let [request {:params      {:resource-name email/resource-type}
                 :body        {:parent  user-id
                               :address email}
                 :nuvla/authn (user-id-identity user-id)}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      (do
        (when validated (callback-email-utils/validate-email! resource-id))
        resource-id)
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


(defn create-customer
  [user-id customer]
  (let [request {:params      {:resource-name customer-resource-url}
                 :body        (assoc customer :parent user-id)
                 :nuvla/authn auth/internal-identity}
        {{:keys [status resource-id] :as body} :body} (crud/add request)]
    (if (= status 201)
      resource-id
      (throw (ex-info (format "could not create customer for '%s'" user-id) body)))))


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
  [user-id & {:keys [email email-validated password username customer]
              :or   {email-validated false}}]

  (when email
    (create-identifier user-id email))

  (when username
    (create-identifier user-id username))

  (let [credential-id (when password (create-hashed-password user-id password))
        email-id      (when email (create-email user-id email :validated email-validated))]

    (update-user user-id (cond-> {:id user-id}
                                 credential-id (assoc :credential-password credential-id)
                                 email-id (assoc :email email-id))))

  (when customer
    (create-customer user-id customer)))


(defn active-claim->customer
  [active-claim]
  (some-> customer-resource-url
          (crud/query-as-admin
            {:cimi-params
             {:filter (parser/parse-cimi-filter
                        (format "parent='%s'" active-claim))}})
          second
          first))


(defn active-claim->subscription-map
  [active-claim]
  (try
    (some-> active-claim
            active-claim->customer
            :subscription-id
            pricing-impl/retrieve-subscription
            pricing-impl/subscription->map)
    (catch Exception _)))


(defn customer-has-subscription-in-states?
  [active-claim pred-fn]
  (boolean
    (try
      (some-> active-claim
              active-claim->subscription-map
              :status
              (pred-fn))
      (catch Exception _))))


(defn customer-has-active-subscription?
  [active-claim]
  (customer-has-subscription-in-states? active-claim #{"active" "trialing" "past_due"}))


(defn customer-has-trialing-subscription?
  [active-claim]
  (customer-has-subscription-in-states? active-claim #{"trialing"}))


(defn throw-user-hasnt-active-subscription
  [request]
  (let [active-claim (auth/current-active-claim request)]
    (when (and config-nuvla/*stripe-api-key*
               (not (customer-has-active-subscription? active-claim)))
      (throw (r/ex-response "An active subscription is required!" 402)))))
