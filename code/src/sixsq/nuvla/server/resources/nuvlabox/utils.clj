(ns sixsq.nuvla.server.resources.nuvlabox.utils
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-tmpl-api]
    [sixsq.nuvla.server.resources.credential-template-infrastructure-service-openvpn-nuvlabox
     :as ctison]
    [sixsq.nuvla.server.resources.infrastructure-service :as infra-service]
    [sixsq.nuvla.server.resources.infrastructure-service-group :as isg]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.util.response :as r]))


(defn short-nb-id
  [nuvlabox-id]
  (when-let [short-id (some-> nuvlabox-id
                              u/id->uuid
                              (str/split #"-")
                              first)]
    short-id))


(defn format-nb-name
  [name id-or-short-id]
  (or name id-or-short-id))


(defn create-infrastructure-service-group
  "Create an infrastructure service group for the NuvlaBox and populate with
   the NuvlaBox services. Returns the possibly modified nuvlabox."
  [{:keys [id owner name] :as nuvlabox}]
  (let [isg-acl  {:owners   ["group/nuvla-admin"]
                  :view-acl [owner]}
        skeleton {:name        (str (format-nb-name name (short-nb-id id)) " service group")
                  :description (str "services available on " (format-nb-name name id))
                  :parent      id
                  :acl         isg-acl}
        {:keys [status body] :as resp} (isg/create-infrastructure-service-group skeleton)]
    (if (= 201 status)
      (assoc nuvlabox :infrastructure-service-group (:resource-id body))
      (let [msg (str "creating infrastructure-service-group resource failed:"
                     status (:message body))]
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
                   :claims  #{id "group/nuvla-user" "group/nuvla-anon" "group/nuvla-nuvlabox"}}

        cred-acl  {:owners    ["group/nuvla-admin"]
                   :view-meta [owner]
                   :delete    [owner]}

        cred-tmpl {:name        (format-nb-name name (short-nb-id id))
                   :description (str/join " " ["Generated API Key for " (format-nb-name name id)])
                   :parent      id
                   :acl         cred-acl
                   :template    {:href    (str "credential-template/" cred-tmpl-api/method)
                                 :subtype cred-tmpl-api/credential-subtype
                                 :method  cred-tmpl-api/method
                                 :ttl     0}}

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
  (let [filter  (format "parent='%s'" nuvlabox-id)
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin isg/resource-type options)
        second
        first
        :id)))


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


(defn create-minio-service
  [nuvlabox-id nuvlabox-name nuvlabox-owner isg-id endpoint]
  (if endpoint
    (let [acl     {:owners [nuvlabox-owner]}
          request {:params      {:resource-name infra-service/resource-type}
                   :body        {:name        (format-nb-name nuvlabox-name
                                                              (short-nb-id nuvlabox-id))
                                 :description (str "Minio (S3) on "
                                                   (format-nb-name nuvlabox-name nuvlabox-id))
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
  [nuvlabox-id nuvlabox-name nuvlabox-owner isg-id endpoint]
  (if endpoint
    (let [acl     {:owners [nuvlabox-owner]}
          request {:params      {:resource-name infra-service/resource-type}
                   :body        {:name        (str "Swarm "
                                                   (format-nb-name nuvlabox-name
                                                                   (short-nb-id nuvlabox-id)))
                                 :description (str "Docker Swarm on "
                                                   (format-nb-name nuvlabox-name nuvlabox-id))
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
  [nuvlabox-id nuvlabox-name nuvlabox-owner swarm-id key cert ca]
  (when swarm-id
    (if (and key cert ca)
      (let [acl     {:owners [nuvlabox-owner]}
            request {:params      {:resource-name credential/resource-type}
                     :body        {:name        (format-nb-name nuvlabox-name
                                                                (short-nb-id nuvlabox-id))
                                   :description (str "Docker Swarm client credential linked to "
                                                     (format-nb-name nuvlabox-name nuvlabox-id))
                                   :parent      swarm-id
                                   :acl         acl
                                   :template    (cond->
                                                  {:href "credential-template/infrastructure-service-swarm"
                                                   :cert cert
                                                   :key  key}
                                                  ca (assoc :ca ca))}
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
        nil))))


(defn get-openvpn-cred
  "Searches for an existing openvpn credential tied to the given nuvlabox.
   If found, the identifier is returned."
  [vpn-server-id user-id]
  (let [filter  (str "subtype='" ctison/credential-subtype "' and parent='" vpn-server-id
                     "' and openvpn-certificate-owner='" user-id "'")
        options {:cimi-params {:filter (parser/parse-cimi-filter filter)
                               :select ["id"]}}]
    (-> (crud/query-as-admin credential/resource-type options)
        second
        first
        :id)))


(defn delete-openvpn-cred
  [id auth-info]
  (crud/delete {:params      {:uuid          (some-> id (str/split #"/") second)
                              :resource-name credential/resource-type}
                :nuvla/authn auth-info}))


(defn create-openvpn-cred
  [nuvlabox-id nuvlabox-name vpn-server-id openvpn-csr auth-info]
  (let [acl  {:owners [nuvlabox-id]}
        tmpl {:name        (format-nb-name nuvlabox-name (short-nb-id nuvlabox-id))
              :description (str/join " " ["Generated VPN Key for "
                                          (format-nb-name nuvlabox-name nuvlabox-id)])
              :parent      vpn-server-id
              :template    {:href        (str "credential-template/" ctison/method)
                            :subtype     ctison/credential-subtype
                            :method      ctison/method
                            :openvpn-csr openvpn-csr}}

        {:keys [status body] :as resp} (credential/create-credential tmpl auth-info)
        {:keys [resource-id]} body]
    (if (= 201 status)
      resource-id
      (let [msg (str "creating credential openvpn resource failed:" status (:message body))]
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
  [nuvlabox-id nuvlabox-name nuvlabox-owner swarm-id scope token]
  (when swarm-id
    (if (and scope token)
      (let [acl     {:owners [nuvlabox-owner]}
            request {:params      {:resource-name credential/resource-type}
                     :body        {:name        (format-nb-name nuvlabox-name
                                                                (short-nb-id nuvlabox-id))
                                   :description (str "Docker Swarm token linked to "
                                                     (format-nb-name nuvlabox-name nuvlabox-id))
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
      (log/info "skipping creation of swarm token; either scope or token is missing"))))


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
  [nuvlabox-id nuvlabox-name nuvlabox-owner minio-id access-key secret-key]
  (when minio-id
    (if (and access-key secret-key)
      (let [acl     {:owners [nuvlabox-owner]}
            request {:params      {:resource-name credential/resource-type}
                     :body        {:name        (format-nb-name nuvlabox-name
                                                                (short-nb-id nuvlabox-id))
                                   :description (str "Minio (S3) credential linked to "
                                                     (format-nb-name nuvlabox-name nuvlabox-id))
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
          (let [msg (str "cannot create minio service credential for "
                         minio-id " linked to " nuvlabox-id)]
            (throw (ex-info msg (r/map-response msg 400 "")))))))))


(defn commission
  [{:keys [id name owner vpn-server-id] :as resource}
   {{:keys [swarm-endpoint
            swarm-token-manager swarm-token-worker
            swarm-client-key swarm-client-cert swarm-client-ca
            minio-endpoint
            minio-access-key minio-secret-key
            openvpn-csr]} :body :as request}]

  ;; This code will not create duplicate resources when commission is called multiple times.
  ;; However, it won't update those resources if the content changes.
  ;; FIXME: allow updates of existing resources
  (when-let [isg-id (get-isg-id id)]
    (let [swarm-id (or
                     (get-swarm-service isg-id)
                     (create-swarm-service id name owner isg-id swarm-endpoint))
          minio-id (or
                     (get-minio-service isg-id)
                     (create-minio-service id name owner isg-id minio-endpoint))]

      (when swarm-id
        (or
          (get-swarm-cred swarm-id)
          (create-swarm-cred id name owner swarm-id swarm-client-key
                             swarm-client-cert swarm-client-ca))
        (or
          (get-swarm-token swarm-id "MANAGER")
          (create-swarm-token id name owner swarm-id "MANAGER" swarm-token-manager))
        (or
          (get-swarm-token swarm-id "WORKER")
          (create-swarm-token id name owner swarm-id "WORKER" swarm-token-worker)))

      (when minio-id
        (or
          (get-minio-cred minio-id)
          (create-minio-cred id name owner minio-id minio-access-key minio-secret-key)))

      (when (and vpn-server-id openvpn-csr)
        (let [user-id         (auth/current-user-id request)
              openvpn-cred-id (get-openvpn-cred vpn-server-id user-id)
              authn-info      (auth/current-authentication request)]
          (when openvpn-cred-id
            (delete-openvpn-cred openvpn-cred-id authn-info))
          (create-openvpn-cred id name vpn-server-id openvpn-csr authn-info)))
      )))
