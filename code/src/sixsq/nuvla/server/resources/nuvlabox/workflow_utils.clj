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
    [sixsq.nuvla.server.resources.nuvlabox-cluster :as nb-cluster]
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
        {:keys [status body] :as _resp} (isg/create-infrastructure-service-group skeleton)]
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
        {:keys [status body] :as _resp} (isg/update-infrastructure-service-group isg-id body)]
    (if (= 200 status)
      nuvlabox
      (let [msg (str "creating infrastructure-service-group resource failed:"
                     status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn create-nuvlabox-status
  [{:keys [id version acl] :as nuvlabox}]
  (let [{:keys [status body] :as _resp} (nb-status/create-nuvlabox-status version id acl)]
    (if (= 201 status)
      (assoc nuvlabox :nuvlabox-status (:resource-id body))
      (let [msg (str "creating nuvlabox-status resource failed:" status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn update-nuvlabox-status
  [status-id {:keys [id acl] :as nuvlabox}]
  (let [{:keys [status body] :as _resp} (nb-status/update-nuvlabox-status status-id id acl)]
    (if (= 200 status)
      nuvlabox
      (let [msg (str "updating nuvlabox-status resource failed:" status (:message body))]
        (throw (r/ex-bad-request msg))))))


(defn create-nuvlabox-api-key
  "Create api key that allow NuvlaBox to update it's own state."
  [{:keys [id name acl] :as nuvlabox}]
  (let [identity  {:user-id      id
                   :active-claim id
                   :claims       #{id "group/nuvla-user" "group/nuvla-anon" "group/nuvla-nuvlabox"}}

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

        {:keys [status body] :as _resp} (credential/create-credential cred-tmpl identity)
        {:keys [resource-id secret-key]} body]
    (if (= 201 status)
      [(assoc nuvlabox :credential-api-key resource-id)
       {:api-key    resource-id
        :secret-key secret-key}]
      (let [msg (str "creating credential api-secret resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn create-ssh-key
  "Create SSH key to be associated with the NuvlaBox"
  [ssh-cred-body]
  (let [{:keys [status body] :as _resp} (credential/create-credential ssh-cred-body auth/internal-identity)
        {:keys [resource-id public-key private-key]} body]

    (if (= 201 status)
      {:id          resource-id
       :public-key  public-key
       :private-key private-key}
      (let [msg (str "creating credential ssh-key resource failed:" status (:message body))]
        (r/ex-bad-request msg)))))


(defn update-nuvlabox-api-key
  [cred-id {:keys [id name acl] :as nuvlabox}]
  (let [cred-acl (utils/set-acl-nuvlabox-view-only acl)

        body     {:name        (utils/format-nb-name name (utils/short-nb-id cred-id))
                  :description (str/join " " ["Generated API Key for "
                                              (utils/format-nb-name name id)])
                  :acl         cred-acl}

        {:keys [status body] :as _resp} (credential/update-credential
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


(def get-minio-service (partial get-service "s3"))


(defn create-coe-service
  [nuvlabox-id nuvlabox-name nuvlabox-acl isg-id endpoint tags capabilities subtype]
  (if endpoint
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:resource-name infra-service/resource-type}
                   :body        {:name        (str "Infra "
                                                   (utils/format-nb-name
                                                     nuvlabox-name (utils/short-nb-id nuvlabox-id)))
                                 :description (str "NuvlaBox compute infrastructure on "
                                                   (utils/format-nb-name nuvlabox-name nuvlabox-id))
                                 :parent      isg-id
                                 :acl         acl
                                 :template    (cond->
                                                {:href     "infrastructure-service-template/generic"
                                                 :endpoint endpoint
                                                 :subtype  subtype}
                                                tags (assoc :tags tags)
                                                capabilities (assoc :capabilities capabilities))}
                   :nuvla/authn auth/internal-identity}
          {{:keys [resource-id]} :body status :status} (crud/add request)]

      (if (= 201 status)
        (do
          (log/info subtype " service" resource-id "created")
          resource-id)
        (let [msg (str "cannot create " subtype " service for " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))
    (do
      (log/info subtype " endpoint not specified; skipping creation of infrastructure-service")
      nil)))


(defn update-coe-service
  [nuvlabox-id nuvlabox-name nuvlabox-acl isg-id endpoint tags capabilities subtype]
  (when-let [resource-id (get-service subtype isg-id)]
    (let [acl     (utils/set-acl-nuvlabox-view-only nuvlabox-acl)
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name infra-service/resource-type}
                   :body        (cond->
                                  {:name        (str (str/capitalize subtype) " "
                                                     (utils/format-nb-name
                                                       nuvlabox-name
                                                       (utils/short-nb-id nuvlabox-id)))
                                   :description (str (str/capitalize subtype) " cluster on "
                                                     (utils/format-nb-name
                                                       nuvlabox-name nuvlabox-id))
                                   :acl         acl}
                                  tags (assoc :tags tags)
                                  endpoint (assoc :endpoint endpoint)
                                  capabilities (assoc :capabilities capabilities))
                   :nuvla/authn auth/internal-identity}
          {status :status} (crud/edit request)]

      (if (= 200 status)
        (do
          (log/info subtype "service" resource-id "updated")
          resource-id)
        (let [msg (str "cannot update " subtype " service for " nuvlabox-id)]
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


(defn get-coe-cred
  "Searches for an existing credential tied to the given service.
   If found, the identifier is returned."
  [subtype is-id]
  (let [filter  (format "subtype='%s' and parent='%s'" subtype is-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin credential/resource-type options)
        second
        first
        :id)))


(defn create-coe-cred
  [nuvlabox-id nuvlabox-name nuvlabox-acl coe-id key cert ca subtype]
  (if (and key cert ca)
    (let [acl     (-> nuvlabox-acl
                      (utils/set-acl-nuvlabox-view-only)
                      (assoc :manage (:view-meta nuvlabox-acl))) ;; add manage to allow check cred
          tmpl    (cond->
                    {:href (str "credential-template/" subtype)
                     :cert cert
                     :key  key}
                    ca (assoc :ca ca))
          request {:params      {:resource-name credential/resource-type}
                   :body        {:name        (utils/format-nb-name nuvlabox-name
                                                                    (utils/short-nb-id nuvlabox-id))
                                 :description (str subtype " client credential linked to "
                                                   (utils/format-nb-name nuvlabox-name nuvlabox-id))
                                 :parent      coe-id
                                 :acl         acl
                                 :template    tmpl}
                   :nuvla/authn auth/internal-identity}
          {{:keys [resource-id]} :body status :status} (crud/add request)]

      (if (= 201 status)
        (do
          (log/info subtype " service credential" resource-id "created")
          resource-id)
        (let [msg (str "cannot create " subtype " service credential for "
                       coe-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))
    (do
      (log/info "skipping creation of " subtype " credential; key, cert, or ca is missing")
      nil)))


(defn update-coe-cred
  [nuvlabox-id nuvlabox-name nuvlabox-acl coe-id key cert ca subtype]
  (when-let [resource-id (get-coe-cred subtype coe-id)]
    (let [acl     (-> nuvlabox-acl
                      (utils/set-acl-nuvlabox-view-only)
                      (assoc :manage (:view-meta nuvlabox-acl)))
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name credential/resource-type}
                   :body        (cond->
                                  {:name        (utils/format-nb-name
                                                  nuvlabox-name
                                                  (utils/short-nb-id nuvlabox-id))
                                   :description (str "NuvlaBox credential linked to "
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
          (log/info subtype " service credential" resource-id "updated")
          resource-id)
        (let [msg (str "cannot update " subtype " service credential for "
                       coe-id " linked to " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn get-vpn-cred
  "Searches for an existing vpn credential tied to the given nuvlabox.
   If found, the identifier is returned."
  [vpn-server-id active-claim]
  (let [filter  (str "subtype='" ctison/credential-subtype "' and parent='" vpn-server-id
                     "' and vpn-certificate-owner='" active-claim "'")
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin credential/resource-type options)
        second
        first
        :id)))


(defn delete-resource
  [id authn-info]
  (when id
    (crud/delete {:params      {:uuid          (u/id->uuid id)
                                :resource-name (u/id->resource-type id)}
                  :nuvla/authn authn-info})))


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

        {:keys [status body] :as _resp} (credential/create-credential tmpl auth-info)
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


(defn get-nuvlabox-cluster
  "Searches for an nuvlabox cluster, given the cluster ID"
  [cluster-id]
  (let [filter  (format "cluster-id='%s'" cluster-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)}}]
    (-> (crud/query-as-admin nb-cluster/resource-type options)
      second
      first)))


(defn create-minio-cred
  [nuvlabox-id nuvlabox-name nuvlabox-acl minio-id access-key secret-key]
  (when (and access-key secret-key)
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


(defn update-nuvlabox-cluster
  [nuvlabox-id cluster-id cluster-node-id cluster-managers cluster-workers]
  (when-let [cluster (get-nuvlabox-cluster cluster-id)]
    (let [resource-id  (:id cluster)
          body  (if (and cluster-id cluster-node-id)
                  (cond->
                    {}
                    (some #{cluster-node-id} (:workers cluster)) (assoc :workers (:workers cluster)))
                  (cond->
                    {}
                    cluster-managers (assoc :managers cluster-managers)
                    cluster-workers (assoc :workers (if cluster-workers
                                                      cluster-workers
                                                      []))))
          request {:params      {:uuid          (u/id->uuid resource-id)
                                 :resource-name nb-cluster/resource-type}
                   :body        body
                   :nuvla/authn auth/internal-identity}
          {status :status} (crud/edit request)]
      (if (= 200 status)
        (do
          (log/info "nuvlabox cluster " resource-id "updated")
          resource-id)
        (let [msg (str "cannot update nuvlabox cluster "
                    resource-id " with ID to " cluster-id
                    ", from NuvlaBox commissioning in " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 ""))))))))


(defn create-nuvlabox-cluster
  [nuvlabox-id nuvlabox-name cluster-id cluster-orchestrator cluster-managers cluster-workers]
    (let [request {:params      {:resource-name nb-cluster/resource-type}
                   :body        {:name        (str "Cluster-" cluster-id)
                                 :description (str "NuvlaBox cluster created by "
                                                (or nuvlabox-name nuvlabox-id))
                                 :orchestrator (or cluster-orchestrator "swarm")
                                 :cluster-id  cluster-id
                                 :managers    cluster-managers
                                 :workers     (if cluster-workers
                                                cluster-workers
                                                [])
                                 :version     2}
                   :nuvla/authn auth/internal-identity}
          {{:keys [resource-id]} :body status :status} (crud/add request)]

      (if (= 201 status)
        (do
          (log/info "NuvlaBox cluster " resource-id "created")
          resource-id)
        (let [msg (str "cannot create NuvlaBox cluster "
                    cluster-id " from NuvlaBox " nuvlabox-id)]
          (throw (ex-info msg (r/map-response msg 400 "")))))))


(defn commission
  [{:keys [id name acl vpn-server-id infrastructure-service-group] :as _resource}
   {{:keys [tags
            capabilities
            swarm-endpoint
            swarm-token-manager swarm-token-worker
            swarm-client-key swarm-client-cert swarm-client-ca
            minio-endpoint
            minio-access-key minio-secret-key
            vpn-csr
            kubernetes-endpoint
            kubernetes-client-key kubernetes-client-cert kubernetes-client-ca
            cluster-id cluster-node-id cluster-orchestrator cluster-managers cluster-workers
            removed]} :body :as request}]
  (when-let [isg-id infrastructure-service-group]
    (let [removed-set    (if (coll? removed) (set removed) #{})
          swarm-removed? (contains? removed-set "swarm-endpoint")
          swarm-id       (or
                           (when swarm-removed? (get-service "swarm" isg-id))
                           (update-coe-service id name acl isg-id swarm-endpoint
                                               tags capabilities "swarm")
                           (create-coe-service id name acl isg-id swarm-endpoint
                                               tags capabilities "swarm"))
          kubernetes-id  (or
                           (update-coe-service id name acl isg-id kubernetes-endpoint
                                               tags capabilities "kubernetes")
                           (create-coe-service id name acl isg-id kubernetes-endpoint
                                               tags capabilities "kubernetes"))
          minio-id       (or
                           (update-minio-service id name acl isg-id minio-endpoint)
                           (create-minio-service id name acl isg-id minio-endpoint))]

      (when (and cluster-id cluster-managers)
        (or
          (update-nuvlabox-cluster id cluster-id nil cluster-managers cluster-workers)
          (create-nuvlabox-cluster id name cluster-id cluster-orchestrator cluster-managers cluster-workers)))

      (when (and cluster-id cluster-node-id)
        (update-nuvlabox-cluster id cluster-id cluster-node-id cluster-managers cluster-workers))

      (when swarm-id
        (or
          (update-coe-cred id name acl swarm-id swarm-client-key
                           swarm-client-cert swarm-client-ca "infrastructure-service-swarm")
          (create-coe-cred id name acl swarm-id swarm-client-key
                           swarm-client-cert swarm-client-ca "infrastructure-service-swarm"))
        (or
          (update-swarm-token id name acl swarm-id "MANAGER" swarm-token-manager)
          (create-swarm-token id name acl swarm-id "MANAGER" swarm-token-manager))
        (or
          (update-swarm-token id name acl swarm-id "WORKER" swarm-token-worker)
          (create-swarm-token id name acl swarm-id "WORKER" swarm-token-worker)))

      (when kubernetes-id
        (or
          (update-coe-cred id name acl kubernetes-id kubernetes-client-key
                           kubernetes-client-cert kubernetes-client-ca "infrastructure-service-kubernetes")
          (create-coe-cred id name acl kubernetes-id kubernetes-client-key
                           kubernetes-client-cert kubernetes-client-ca "infrastructure-service-kubernetes")))

      (when minio-id
        (or
          (update-minio-cred id name acl minio-id minio-access-key minio-secret-key)
          (create-minio-cred id name acl minio-id minio-access-key minio-secret-key)))

      (when (and vpn-server-id vpn-csr)
        (let [active-claim (auth/current-active-claim request)
              vpn-cred-id  (get-vpn-cred vpn-server-id active-claim)
              authn-info   (auth/current-authentication request)]
          (when vpn-cred-id
            (delete-resource vpn-cred-id authn-info))
          (create-vpn-cred id name vpn-server-id vpn-csr authn-info)))

      (when (contains? removed-set "swarm-token-manager")
        (delete-resource (get-swarm-token swarm-id "MANAGER") auth/internal-identity))

      (when (contains? removed-set "swarm-token-worker")
        (delete-resource (get-swarm-token swarm-id "WORKER") auth/internal-identity))

      (when (contains? removed-set "swarm-client-key")
        (delete-resource (get-coe-cred "infrastructure-service-swarm" swarm-id)
                         auth/internal-identity))

      (when swarm-removed?
        (delete-resource swarm-id auth/internal-identity))

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
            {status :status :as _resp} (crud/edit request)]
        (if (= 200 status)
          (log/info "nuvlabox peripheral" id "updated")
          (let [msg (str "cannot update nuvlabox peripheral for " nuvlabox-id)]
            (throw (ex-info msg (r/map-response msg 400 "")))))))))
