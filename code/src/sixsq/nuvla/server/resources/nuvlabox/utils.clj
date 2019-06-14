(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-tmpl-api]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as service-group]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.response :as r]))


(defn short-nb-id
  [nuvlabox-id]
  (when-let [short-id (some-> nuvlabox-id
                              u/id->uuid
                              (str/split #"-")
                              first)]
    (str "NB " short-id)))


(defn create-infrastructure-service-group
  "Create an infrastructure service group for the NuvlaBox and populate with
   the NuvlaBox services. Returns the possibly modified nuvlabox."
  [{:keys [id owner] :as nuvlabox}]
  (let [isg-acl  {:owners   ["group/nuvla-admin"]
                  :view-acl [owner]}
        skeleton {:name        (str (short-nb-id id) " service group")
                  :description (str "services available on " id)
                  :parent      id
                  :acl         isg-acl}
        {:keys [status body] :as resp} (service-group/create-infrastructure-service-group skeleton)]
    (if (= 201 status)
      (assoc nuvlabox :infrastructure-service-group (:resource-id body))
      (let [msg (str "creating infrastructure-service-group resource failed:" status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn create-nuvlabox-status
  "Create an infrastructure service group for the NuvlaBox and populate with
   the NuvlaBox services. Returns the possibly modified nuvlabox."
  [{:keys [id version owner] :as nuvlabox}]
  (let [{:keys [status body] :as resp} (nb-status/create-nuvlabox-status version id owner)]
    (if (= 201 status)
      (assoc nuvlabox :nuvlabox-status (:resource-id body))
      (let [msg (str "creating nuvlabox-status resource failed:" status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn create-nuvlabox-api-key
  "Create api key that allow NuvlaBox to update it's own state."
  [{:keys [id name owner] :as nuvlabox}]
  (let [identity  {:user-id id
                   :claims  #{id "group/nuvla-user" "group/nuvla-anon"}}

        cred-acl  {:owners    ["group/nuvla-admin"]
                   :view-meta [owner]
                   :delete    [owner]}

        cred-tmpl {:name        (str (short-nb-id id) " API Key")
                   :description (str/join " " ["Generated API Key for" name (str "(" id ")")])
                   :parent      id
                   :template    {:href    (str "credential-template/" cred-tmpl-api/method)
                                 :subtype cred-tmpl-api/credential-subtype
                                 :method  cred-tmpl-api/method
                                 :ttl     0
                                 :acl     cred-acl}}

        {:keys [status body] :as resp} (credential/create-credential cred-tmpl identity)
        {:keys [resource-id secret-key]} body]
    (if (= 201 status)
      {:api-key    resource-id
       :secret-key secret-key}
      (let [msg (str "creating credential api-secret resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn get-isg-id
  "Finds the infrastructure-service-group that is associated with the given
   nuvlabox-id."
  [nuvlabox-id]
  (let [filter (format "parent='%s'" nuvlabox-id)
        body   {:cimi-params {:filter (parser/parse-cimi-filter filter)
                              :select ["id"]}
                :nuvla/authn auth/internal-identity}]
    (-> (db/query isg/resource-type body)
        second
        first
        :id)))


(defn get-service-credential-id
  "Finds the credential linked to the given infrastructure-service id."
  [service-id]
  (let [filter (format "parent='%s'" service-id)
        body   {:cimi-params {:filter (parser/parse-cimi-filter filter)
                              :select ["id"]}
                :nuvla/authn auth/internal-identity}]
    (-> (db/query infra-service/resource-type body)
        second
        first
        :id)))


(defn create-minio-service
  [nuvlabox-id owner isg-id endpoint]
  (if endpoint
    (let [acl     {:owners [owner]}
          request {:params      {:resource-name infra-service/resource-type}
                   :body        {:name        (str "Minio " (short-nb-id nuvlabox-id))
                                 :description (str "Minio (S3) on " nuvlabox-id)
                                 :parent      isg-id
                                 :acl         acl
                                 :template    {:href     "infrastructure-service-template/generic"
                                               :endpoint endpoint
                                               :subtype  "s3"}}
                   :nuvla/authn auth/internal-identity}
          {{:keys [resource-id]} :body status :status} (crud/add request)]

      (if (= 201 status)
        (do
          (log/info "minio service" resource-id "created")
          resource-id)
        (let [msg (str "cannot create minio service for " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))
    (log/info "minio endpoint not specified; skipping creation of infrastructure-service")))


(defn get-service
  "Searches for an existing infrastructure-service of the given subtype and
   linked to the given infrastructure-service-group. If found, the identifier
   is returned."
  [subtype isg-id]
  (let [filter (format "subtype='%s' and parent='%s'" subtype isg-id)
        body   {:cimi-params {:filter (parser/parse-cimi-filter filter)
                              :select ["id"]}
                :nuvla/authn auth/internal-identity}]
    (-> (db/query infra-service/resource-type body)
        second
        first
        :id)))


(def get-swarm-service (partial get-service "swarm"))


(def get-minio-service (partial get-service "s3"))


(defn create-swarm-service
  [nuvlabox-id owner isg-id endpoint]
  (if endpoint
    (let [acl     {:owners [owner]}
          request {:params      {:resource-name infra-service/resource-type}
                   :body        {:name        (str "Swarm " (short-nb-id nuvlabox-id))
                                 :description (str "Docker Swarm on " nuvlabox-id)
                                 :parent      isg-id
                                 :acl         acl
                                 :template    {:href     "infrastructure-service-template/generic"
                                               :endpoint endpoint
                                               :subtype  "swarm"}}
                   :nuvla/authn auth/internal-identity}
          {{:keys [resource-id]} :body status :status} (crud/add request)]

      (if (= 201 status)
        (do
          (log/info "swarm service" resource-id "created")
          resource-id)
        (let [msg (str "cannot create swarm service for " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))
    (do
      (log/info "swarm endpoint not specified; skipping creation of infrastructure-service")
      nil)))


(defn create-swarm-cred
  [nuvlabox-id owner swarm-id key cert ca]
  (when swarm-id
    (if (and key cert ca)
      (let [acl     {:owners [owner]}
            request {:params      {:resource-name credential/resource-type}
                     :body        {:name        (str "Swarm TLS " (short-nb-id nuvlabox-id))
                                   :description (str "Docker Swarm client credential for " swarm-id " linked to " nuvlabox-id)
                                   :parent      swarm-id
                                   :acl         acl
                                   :template    (cond-> {:href "credential-template/infrastructure-service-swarm"
                                                         :cert cert
                                                         :key  key}
                                                        ca (assoc :ca ca))}
                     :nuvla/authn auth/internal-identity}
            {{:keys [resource-id]} :body status :status} (crud/add request)]

        (if (= 201 status)
          (do
            (log/info "swarm service credential" resource-id "created")
            resource-id)
          (let [msg (str "cannot create swarm service credential for " swarm-id " linked to " nuvlabox-id)]
            (throw (ex-info msg (r/map-response msg 400 ""))))))
      (do
        (log/info "skipping creation of swarm credential; key, cert, or ca is missing")
        nil))))


(defn get-swarm-token
  "Searches for an existing swarm token credential tied to the given service.
   If found, the identifier is returned."
  [swarm-id scope]
  (let [filter (format "subtype='swarm-token' and scope='%s' and parent='%s'" scope swarm-id)
        body   {:cimi-params {:filter (parser/parse-cimi-filter filter)
                              :select ["id"]}
                :nuvla/authn auth/internal-identity}]
    (-> (db/query credential/resource-type body)
        second
        first
        :id)))


(defn create-swarm-token
  [nuvlabox-id owner swarm-id scope token]
  (when swarm-id
    (if (and scope token)
      (let [acl     {:owners [owner]}
            request {:params      {:resource-name credential/resource-type}
                     :body        {:name        (str "Swarm Token " (short-nb-id nuvlabox-id))
                                   :description (str "Docker Swarm token for " swarm-id " linked to " nuvlabox-id)
                                   :parent      swarm-id
                                   :acl         acl
                                   :template    {:href  "credential-template/swarm-token"
                                                 :scope scope
                                                 :token token}}
                     :nuvla/authn auth/internal-identity}
            {{:keys [resource-id]} :body status :status} (crud/add request)]

        (if (= 201 status)
          (do
            (log/info "swarm token credential" resource-id "created")
            resource-id)
          (let [msg (str "cannot create swarm token credential for " swarm-id " linked to " nuvlabox-id)]
            (throw (ex-info msg (r/map-response msg 400 ""))))))
      (log/info "skipping creation of swarm token; either scope or token is missing"))))


(defn get-minio-cred
  "Searches for an existing minio credential tied to the given service. If
   found, the identifier is returned."
  [minio-id]
  (let [filter (format "subtype='infrastructure-service-minio' and parent='%s'" minio-id)
        body   {:cimi-params {:filter (parser/parse-cimi-filter filter)
                              :select ["id"]}
                :nuvla/authn auth/internal-identity}]
    (-> (db/query credential/resource-type body)
        second
        first
        :id)))


(defn create-minio-cred
  [nuvlabox-id owner minio-id access-key secret-key]
  (when minio-id
    (if (and access-key secret-key)
      (let [acl     {:owners [owner]}
            request {:params      {:resource-name credential/resource-type}
                     :body        {:name        (str "Minio " (short-nb-id nuvlabox-id))
                                   :description (str "Minio (S3) credential for " minio-id " linked to " nuvlabox-id)
                                   :parent      minio-id
                                   :acl         acl
                                   :template    {:href       "credential-template/infrastructure-service-minio"
                                                 :access-key access-key
                                                 :secret-key secret-key}}
                     :nuvla/authn auth/internal-identity}
            {{:keys [resource-id]} :body status :status} (crud/add request)]

        (if (= 201 status)
          (do
            (log/info "minio service credential" resource-id "created")
            resource-id)
          (let [msg (str "cannot create minio service credential for " minio-id " linked to " nuvlabox-id)]
            (throw (ex-info msg (r/map-response msg 400 "")))))))))


