(ns sixsq.nuvla.server.resources.nuvlabox.workflow-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-tmpl-api]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-nuvlabox
     :as ctison]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [sixsq.nuvla.server.resources.nuvlabox-peripheral :as nb-peripheral]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.resources.nuvlabox.utils :as utils]
    [sixsq.nuvla.server.util.response :as r]))


(defn create-infrastructure-service-group
  [{:keys [id acl name] :as nuvlabox}]
  (let [isg-acl  (utils/set-acl-nuvlabox-view-only acl)
        skeleton {:name        (str (utils/format-nb-name
                                      name (utils/short-nb-id id)) " service group")
                  :description (str "services available on " (utils/format-nb-name name id))
                  :parent      id
                  :acl         isg-acl}
        {:keys [status body] :as resp} (isg/create-infrastructure-service-group skeleton)]
    (if (= 201 status)
      (assoc nuvlabox :infrastructure-service-group (:resource-id body))
      (let [msg (str "creating infrastructure-service-group resource failed:"
                     status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn update-infrastructure-service-group
  [isg-id {:keys [id acl name] :as nuvlabox}]
  (let [isg-acl (utils/set-acl-nuvlabox-view-only acl)

        body    {:name (str (utils/format-nb-name name (utils/short-nb-id id)) " service group")
                 :acl  isg-acl}
        {:keys [status body] :as resp} (isg/update-infrastructure-service-group isg-id body)]
    (if (= 200 status)
      nuvlabox
      (let [msg (str "creating infrastructure-service-group resource failed:"
                     status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn create-nuvlabox-status
  [{:keys [id version acl] :as nuvlabox}]
  (let [{:keys [status body] :as resp} (nb-status/create-nuvlabox-status version id acl)]
    (if (= 201 status)
      (assoc nuvlabox :nuvlabox-status (:resource-id body))
      (let [msg (str "creating nuvlabox-status resource failed:" status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn update-nuvlabox-status
  [status-id {:keys [id acl] :as nuvlabox}]
  (let [{:keys [status body] :as resp} (nb-status/update-nuvlabox-status status-id id acl)]
    (if (= 200 status)
      nuvlabox
      (let [msg (str "updating nuvlabox-status resource failed:" status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn create-nuvlabox-api-key
  "Create api key that allow NuvlaBox to update it's own state."
  [{:keys [id name acl] :as nuvlabox}]
  (let [identity  {:user-id id
                   :claims  #{id "group/nuvla-user" "group/nuvla-anon" "group/nuvla-nuvlabox"}}

        cred-acl  (utils/set-acl-nuvlabox-view-only acl)

        cred-tmpl {:name        (utils/format-nb-name name (utils/short-nb-id id))
                   :description (str/join " " ["Generated API Key for "
                                               (utils/format-nb-name name id)])
                   :parent      id
                   :acl         cred-acl
                   :template    {:href    (str "credential-template/" cred-tmpl-api/method)
                                 :subtype cred-tmpl-api/credential-subtype
                                 :method  cred-tmpl-api/method
                                 :ttl     0}}

        {:keys [status body] :as resp} (credential/create-credential cred-tmpl identity)
        {:keys [resource-id secret-key]} body]
    (if (= 201 status)
      [(assoc nuvlabox :credential-api-key resource-id)
       {:api-key    resource-id
        :secret-key secret-key}]
      (let [msg (str "creating credential api-secret resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn update-nuvlabox-api-key
  [cred-id {:keys [id name acl] :as nuvlabox}]
  (let [cred-acl (utils/set-acl-nuvlabox-view-only acl)

        body     {:name        (utils/format-nb-name name (utils/short-nb-id cred-id))
                  :description (str/join " " ["Generated API Key for "
                                              (utils/format-nb-name name id)])
                  :acl         cred-acl}

        {:keys [status body] :as resp} (credential/update-credential
                                         cred-id body auth/internal-identity)]
    (if (= 200 status)
      nuvlabox
      (let [msg (str "updating credential api-secret resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn get-service-credential-id
  "Finds the credential linked to the given infrastructure-service id."
  [service-id]
  (let [filter  (format "parent='%s'" service-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin infra-service/resource-type options)
        second
        first
        :id)))


(defn get-service
  "Searches for an existing infrastructure-service of the given subtype and
   linked to the given infrastructure-service-group. If found, the identifier
   is returned."
  [subtype isg-id]
  (let [filter  (format "subtype='%s' and parent='%s'" subtype isg-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin infra-service/resource-type options)
        second
        first
        :id)))


(def get-swarm-service (partial get-service "swarm"))


(def get-minio-service (partial get-service "s3"))


(defn create-swarm-service
  [nuvlabox-id nuvlabox-name nuvlabox-acl isg-id endpoint tags]
  (if endpoint
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:resource-name infra-service/resource-type}
                   :body        {:name        (str "Swarm "
                                                   (utils/format-nb-name
                                                     nuvlabox-name (utils/short-nb-id nuvlabox-id)))
                                 :description (str "Docker Swarm on "
                                                   (utils/format-nb-name nuvlabox-name nuvlabox-id))
                                 :parent      isg-id
                                 :acl         acl
                                 :template    (cond->
                                                {:href     "infrastructure-service-template/generic"
                                                 :endpoint endpoint
                                                 :subtype  "swarm"}
                                                tags (assoc :tags tags))}
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


(defn update-swarm-service
  [nuvlabox-id nuvlabox-name nuvlabox-acl isg-id endpoint tags]
  (when-let [resource-id (get-swarm-service isg-id)]
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name infra-service/resource-type}
                   :body        (cond->
                                  {:name        (str "Swarm "
                                                     (utils/format-nb-name
                                                       nuvlabox-name
                                                       (utils/short-nb-id nuvlabox-id)))
                                   :description (str "Docker Swarm on "
                                                     (utils/format-nb-name
                                                       nuvlabox-name nuvlabox-id))
                                   :acl         acl}
                                  tags (assoc :tags tags)
                                  endpoint (assoc :endpoint endpoint))
                   :nuvla/authn auth/internal-identity}
          {status :status} (crud/edit request)]

      (if (= 200 status)
        (do
          (log/info "swarm service" resource-id "updated")
          resource-id)
        (let [msg (str "cannot update swarm service for " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn create-minio-service
  [nuvlabox-id nuvlabox-name nuvlabox-acl isg-id endpoint]
  (if endpoint
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:resource-name infra-service/resource-type}
                   :body        {:name        (utils/format-nb-name nuvlabox-name
                                                                    (utils/short-nb-id nuvlabox-id))
                                 :description (str "Minio (S3) on "
                                                   (utils/format-nb-name nuvlabox-name nuvlabox-id))
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


(defn update-minio-service
  [nuvlabox-id nuvlabox-name nuvlabox-acl isg-id endpoint]
  (when-let [resource-id (get-minio-service isg-id)]
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name infra-service/resource-type}
                   :body        (cond->
                                  {:name        (utils/format-nb-name
                                                  nuvlabox-name
                                                  (utils/short-nb-id nuvlabox-id))
                                   :description (str "Minio (S3) on "
                                                     (utils/format-nb-name
                                                       nuvlabox-name nuvlabox-id))
                                   :parent      isg-id
                                   :acl         acl}
                                  endpoint (assoc :endpoint endpoint))
                   :nuvla/authn auth/internal-identity}
          {status :status} (crud/edit request)]

      (if (= 200 status)
        (do
          (log/info "minio service" resource-id "updated")
          resource-id)
        (let [msg (str "cannot update minio service for " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn get-swarm-cred
  "Searches for an existing swarm credential tied to the given service.
   If found, the identifier is returned."
  [swarm-id]
  (let [filter  (format "subtype='infrastructure-service-swarm' and parent='%s'" swarm-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin credential/resource-type options)
        second
        first
        :id)))


(defn create-swarm-cred
  [nuvlabox-id nuvlabox-name nuvlabox-acl swarm-id key cert ca]
  (if (and key cert ca)
    (let [acl     (-> nuvlabox-acl
                      (utils/set-acl-nuvlabox-view-only)
                      (assoc :manage (:view-meta nuvlabox-acl))) ;; add manage to allow check cred
          tmpl    (cond->
                    {:href "credential-template/infrastructure-service-swarm"
                     :cert cert
                     :key  key}
                    ca (assoc :ca ca))
          request {:params      {:resource-name credential/resource-type}
                   :body        {:name        (utils/format-nb-name nuvlabox-name
                                                                    (utils/short-nb-id nuvlabox-id))
                                 :description (str "Docker Swarm client credential linked to "
                                                   (utils/format-nb-name nuvlabox-name nuvlabox-id))
                                 :parent      swarm-id
                                 :acl         acl
                                 :template    tmpl}
                   :nuvla/authn auth/internal-identity}
          {{:keys [resource-id]} :body status :status} (crud/add request)]

      (if (= 201 status)
        (do
          (log/info "swarm service credential" resource-id "created")
          resource-id)
        (let [msg (str "cannot create swarm service credential for "
                       swarm-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))
    (do
      (log/info "skipping creation of swarm credential; key, cert, or ca is missing")
      nil)))


(defn update-swarm-cred
  [nuvlabox-id nuvlabox-name nuvlabox-acl swarm-id key cert ca]
  (when-let [resource-id (get-swarm-cred swarm-id)]
    (let [acl     (-> nuvlabox-acl
                      (utils/set-acl-nuvlabox-view-only)
                      (assoc :manage (:view-meta nuvlabox-acl)))
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name credential/resource-type}
                   :body        (cond->
                                  {:name        (utils/format-nb-name
                                                  nuvlabox-name
                                                  (utils/short-nb-id nuvlabox-id))
                                   :description (str "Docker Swarm client credential linked to "
                                                     (utils/format-nb-name
                                                       nuvlabox-name nuvlabox-id))
                                   :acl         acl}
                                  ca (assoc :ca ca)
                                  key (assoc :key key)
                                  cert (assoc :cert cert))
                   :nuvla/authn auth/internal-identity}
          {status :status} (crud/edit request)]

      (if (= 200 status)
        (do
          (log/info "swarm service credential" resource-id "updated")
          resource-id)
        (let [msg (str "cannot update swarm service credential for "
                       swarm-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn get-vpn-cred
  "Searches for an existing vpn credential tied to the given nuvlabox.
   If found, the identifier is returned."
  [vpn-server-id user-id]
  (let [filter  (str "subtype='" ctison/credential-subtype "' and parent='" vpn-server-id
                     "' and vpn-certificate-owner='" user-id "'")
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin credential/resource-type options)
        second
        first
        :id)))


(defn delete-vpn-cred
  [id auth-info]
  (crud/delete {:params      {:uuid          (some-> id (str/split #"/") second)
                              :resource-name credential/resource-type}
                :nuvla/authn auth-info}))


(defn create-vpn-cred
  [nuvlabox-id nuvlabox-name vpn-server-id vpn-csr auth-info]
  (let [tmpl {:name        (utils/format-nb-name nuvlabox-name (utils/short-nb-id nuvlabox-id))
              :description (str/join " " ["Generated VPN Key for "
                                          (utils/format-nb-name nuvlabox-name nuvlabox-id)])
              :parent      vpn-server-id
              :template    {:href    (str "credential-template/" ctison/method)
                            :subtype ctison/credential-subtype
                            :method  ctison/method
                            :vpn-csr vpn-csr}}

        {:keys [status body] :as resp} (credential/create-credential tmpl auth-info)
        {:keys [resource-id]} body]
    (if (= 201 status)
      resource-id
      (let [msg (str "creating credential vpn resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn get-swarm-token
  "Searches for an existing swarm token credential tied to the given service.
   If found, the identifier is returned."
  [swarm-id scope]
  (let [filter  (format "subtype='swarm-token' and scope='%s' and parent='%s'" scope swarm-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin credential/resource-type options)
        second
        first
        :id)))


(defn create-swarm-token
  [nuvlabox-id nuvlabox-name nuvlabox-acl swarm-id scope token]
  (if (and scope token)
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:resource-name credential/resource-type}
                   :body        {:name        (utils/format-nb-name nuvlabox-name
                                                                    (utils/short-nb-id nuvlabox-id))
                                 :description (str "Docker Swarm token linked to "
                                                   (utils/format-nb-name nuvlabox-name nuvlabox-id))
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
        (let [msg (str "cannot create swarm token credential for "
                       swarm-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))
    (log/info "skipping creation of swarm token; either scope or token is missing")))


(defn update-swarm-token
  [nuvlabox-id nuvlabox-name nuvlabox-acl swarm-id scope token]
  (when-let [resource-id (get-swarm-token swarm-id scope)]
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name credential/resource-type}
                   :body        (cond->
                                  {:name        (utils/format-nb-name
                                                  nuvlabox-name
                                                  (utils/short-nb-id nuvlabox-id))
                                   :description (str "Docker Swarm token linked to "
                                                     (utils/format-nb-name
                                                       nuvlabox-name nuvlabox-id))
                                   :acl         acl}
                                  scope (assoc :scope scope)
                                  token (assoc :token token))
                   :nuvla/authn auth/internal-identity}
          {status :status} (crud/edit request)]

      (if (= 200 status)
        (do
          (log/info "swarm token credential" resource-id "updated")
          resource-id)
        (let [msg (str "cannot update swarm token credential for "
                       swarm-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn get-minio-cred
  "Searches for an existing minio credential tied to the given service. If
   found, the identifier is returned."
  [minio-id]
  (let [filter  (format "subtype='infrastructure-service-minio' and parent='%s'" minio-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin credential/resource-type options)
        second
        first
        :id)))


(defn create-minio-cred
  [nuvlabox-id nuvlabox-name nuvlabox-acl minio-id access-key secret-key]
  (if (and access-key secret-key)
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          tmpl    {:href       "credential-template/infrastructure-service-minio"
                   :access-key access-key
                   :secret-key secret-key}
          request {:params      {:resource-name credential/resource-type}
                   :body        {:name        (utils/format-nb-name
                                                nuvlabox-name
                                                (utils/short-nb-id nuvlabox-id))
                                 :description (str "Minio (S3) credential linked to "
                                                   (utils/format-nb-name
                                                     nuvlabox-name nuvlabox-id))
                                 :parent      minio-id
                                 :acl         acl
                                 :template    tmpl}
                   :nuvla/authn auth/internal-identity}
          {{:keys [resource-id]} :body status :status} (crud/add request)]

      (if (= 201 status)
        (do
          (log/info "minio service credential" resource-id "created")
          resource-id)
        (let [msg (str "cannot create minio service credential for "
                       minio-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn update-minio-cred
  [nuvlabox-id nuvlabox-name nuvlabox-acl minio-id access-key secret-key]
  (when-let [resource-id (get-minio-cred minio-id)]
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name credential/resource-type}
                   :body        (cond->
                                  {:name        (utils/format-nb-name
                                                  nuvlabox-name
                                                  (utils/short-nb-id nuvlabox-id))
                                   :description (str "Minio (S3) credential linked to "
                                                     (utils/format-nb-name
                                                       nuvlabox-name nuvlabox-id))
                                   :acl         acl}
                                  access-key (assoc :access-key access-key)
                                  secret-key (assoc :secret-key secret-key))
                   :nuvla/authn auth/internal-identity}
          {status :status} (crud/edit request)]

      (if (= 200 status)
        (do
          (log/info "minio service credential" resource-id "updated")
          resource-id)
        (let [msg (str "cannot update minio service credential for "
                       minio-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn commission
  [{:keys [id name acl vpn-server-id infrastructure-service-group] :as resource}
   {{:keys [tags
            swarm-endpoint
            swarm-token-manager swarm-token-worker
            swarm-client-key swarm-client-cert swarm-client-ca
            minio-endpoint
            minio-access-key minio-secret-key
            vpn-csr]} :body :as request}]

  (when-let [isg-id infrastructure-service-group]
    (let [swarm-id (or
                     (update-swarm-service id name acl isg-id swarm-endpoint tags)
                     (create-swarm-service id name acl isg-id swarm-endpoint tags))
          minio-id (or
                     (update-minio-service id name acl isg-id minio-endpoint)
                     (create-minio-service id name acl isg-id minio-endpoint))]

      (when swarm-id
        (or
          (update-swarm-cred id name acl swarm-id swarm-client-key
                             swarm-client-cert swarm-client-ca)
          (create-swarm-cred id name acl swarm-id swarm-client-key
                             swarm-client-cert swarm-client-ca))
        (or
          (update-swarm-token id name acl swarm-id "MANAGER" swarm-token-manager)
          (create-swarm-token id name acl swarm-id "MANAGER" swarm-token-manager))
        (or
          (update-swarm-token id name acl swarm-id "WORKER" swarm-token-worker)
          (create-swarm-token id name acl swarm-id "WORKER" swarm-token-worker)))

      (when minio-id
        (or
          (update-minio-cred id name acl minio-id minio-access-key minio-secret-key)
          (create-minio-cred id name acl minio-id minio-access-key minio-secret-key)))

      (when (and vpn-server-id vpn-csr)
        (let [user-id     (auth/current-user-id request)
              vpn-cred-id (get-vpn-cred vpn-server-id user-id)
              authn-info  (auth/current-authentication request)]
          (when vpn-cred-id
            (delete-vpn-cred vpn-cred-id authn-info))
          (create-vpn-cred id name vpn-server-id vpn-csr authn-info)))
      )))


(defn get-nuvlabox-peripherals-ids
  [id]
  (let [filter  (format "parent='%s'" id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (->> (crud/query-as-admin nb-peripheral/resource-type options)
         second
         (map :id))))


(defn update-peripherals
  [nuvlabox-id nuvlabox-acl]
  (when-let [ids (get-nuvlabox-peripherals-ids nuvlabox-id)]
    (doseq [id ids]
      (let [request {:params      {:uuid          (u/id->uuid id)
                                   :resource-name nb-peripheral/resource-type}
                     :body        {:acl (-> nuvlabox-acl
                                            (utils/set-acl-nuvlabox-view-only
                                              {:owners [nuvlabox-id]})
                                            (assoc :manage (:view-acl nuvlabox-acl)))}
                     :nuvla/authn auth/internal-identity}
            {status :status :as resp} (crud/edit request)]
        (if (= 200 status)
          (log/info "nuvlabox peripheral" id "updated")
          (let [msg (str "cannot update nuvlabox peripheral for " nuvlabox-id)]
            (throw (ex-info msg (r/map-response msg 400 "")))))))))
