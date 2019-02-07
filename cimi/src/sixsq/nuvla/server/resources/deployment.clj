(ns sixsq.nuvla.server.resources.deployment
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.auth.acl :as acl]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.credential :as credential]
    [sixsq.nuvla.server.resources.credential-template-api-key :as cred-api-key]
    [sixsq.nuvla.server.resources.deployment-template :as deployment-template]
    [sixsq.nuvla.server.resources.deployment.utils :as du]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.job :as job]
    [sixsq.nuvla.server.resources.spec.deployment :as deployment-spec]
    [sixsq.nuvla.server.resources.spec.deployment-template :as deployment-template-spec]
    [sixsq.nuvla.util.response :as r]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-tag :deployments)

(def ^:const resource-name "Deployment")

(def ^:const resource-url (u/de-camelcase resource-name))

(def ^:const collection-name "DeploymentCollection")

(def ^:const resource-uri (str c/slipstream-schema-uri resource-name))

(def ^:const collection-uri (str c/slipstream-schema-uri collection-name))

(def ^:const create-uri (str c/slipstream-schema-uri resource-name "Create"))

(def collection-acl {:owner {:principal "ADMIN"
                             :type      "ROLE"}
                     :rules [{:principal "ADMIN"
                              :type      "ROLE"
                              :right     "MODIFY"}
                             {:principal "USER"
                              :type      "ROLE"
                              :right     "MODIFY"}]})

;;
;; validate deployment
;;

(def validate-fn (u/create-spec-validation-fn ::deployment-spec/deployment))
(defmethod crud/validate resource-uri
  [resource]
  (validate-fn resource))

(def validate-create-fn (u/create-spec-validation-fn ::deployment-template-spec/deployment-template-create))
(defmethod crud/validate create-uri
  [resource]
  (validate-create-fn resource))


;;
;; multimethod for ACLs
;;


(defmethod crud/add-acl resource-uri
  [resource request]
  (a/add-acl resource request))


;;
;; CRUD operations
;;

(defn retrieve-deployment-template
  [deployment idmap]
  (let [deployment-tmpl-href (get-in deployment [:deploymentTemplate :href])
        {:keys [body status]} (if (= deployment-tmpl-href deployment-template/generated-url)
                                {:status 200
                                 :body   (du/create-deployment-template (:deploymentTemplate deployment) idmap)}
                                (crud/retrieve {:params   {:uuid          (u/document-id deployment-tmpl-href)
                                                           :resource-name deployment-template/resource-url}
                                                :identity idmap}))]
    (if (= status 200)
      (assoc deployment :deploymentTemplate (dissoc body :operations :acl :id :href))
      (throw (ex-info "" body)))))


(def add-impl (std-crud/add-fn resource-name collection-acl resource-uri))

(defn generate-api-key-secret
  [{:keys [identity] :as request}]
  (let [request-api-key {:params   {:resource-name credential/resource-url}
                         :body     {:template {:href (str "credential-template/" cred-api-key/method)}}
                         :identity identity}
        {{:keys [status resource-id secretKey] :as body} :body :as response} (crud/add request-api-key)]
    (if (= status 201)
      [resource-id secretKey]
      (throw (ex-info "" body)))))


(defmethod crud/add resource-name
  [{:keys [body] :as request}]
  (try
    (a/can-modify? {:acl collection-acl} request)
    (let [idmap (:identity request)
          desc-attrs (u/select-desc-keys body)
          deployment-tmpl-href (get-in body [:deploymentTemplate :href] deployment-template/generated-url)
          [api-key secret] (generate-api-key-secret request)
          deployment (-> body
                         (assoc-in [:deploymentTemplate :href] deployment-tmpl-href)
                         (assoc :resourceURI create-uri)
                         (retrieve-deployment-template idmap)
                         (update-in [:deploymentTemplate] merge desc-attrs) ;; ensure desc attrs are validated
                         crud/validate
                         :deploymentTemplate
                         (assoc-in [:deploymentTemplate :href] deployment-tmpl-href)
                         (assoc :clientAPIKey {:href api-key, :secret secret})
                         (assoc :state "CREATED"))
          create-response (add-impl (assoc request :body deployment))
          href (get-in create-response [:body :resource-id])
          msg (get-in create-response [:body :message])]
      (event-utils/create-event href msg (acl/default-acl (acl/current-authentication request)))
      create-response)
    (catch Exception e
      (or (ex-data e) (throw e)))))

(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))


(def edit-impl (std-crud/edit-fn resource-name))

(defmethod crud/edit resource-name
  [request]
  (edit-impl (update request :body dissoc :clientAPIKey)))


(defn can-delete?
  [{:keys [state] :as resource}]
  (#{"CREATED" "STOPPED"} state))


(defn verify-can-delete
  [{:keys [id state] :as resource}]
  (if (can-delete? resource)
    resource
    (throw (r/ex-response (str "invalid state (" state ") for delete on " id) 409 id))))


(defn delete-impl
  [{{uuid :uuid} :params :as request}]
  (try
    (-> (str (u/de-camelcase resource-name) "/" uuid)
        (db/retrieve request)
        verify-can-delete
        (a/can-modify? request)
        (db/delete request))
    (catch Exception e
      (or (ex-data e) (throw e)))))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))


(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))

(defmethod crud/query resource-name
  [request]
  (query-impl request))


;;
;; actions may be needed by certain authentication methods (notably external
;; methods like GitHub and OpenID Connect) to validate a given session
;;

(defn remove-delete
  [operations]
  (vec (remove #(= (:delete c/action-uri) (:rel %)) operations)))


(defmethod crud/set-operations resource-uri
  [{:keys [id state] :as resource} request]
  (let [start-op {:rel (:start c/action-uri) :href (str id "/start")}
        stop-op {:rel (:stop c/action-uri) :href (str id "/stop")}]
    (cond-> (crud/set-standard-operations resource request)
            (#{"CREATED"} state) (update :operations conj start-op)
            (#{"STARTING" "STARTED" "ERROR"} state) (update :operations conj stop-op)
            (not (can-delete? resource)) (update :operations remove-delete))))


(defmethod crud/do-action [resource-url "start"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)
          user-id (:identity (a/current-authentication request))
          request-job-creation {:identity std-crud/internal-identity
                                :body     {:action         "start_deployment"
                                           :targetResource {:href id}
                                           :priority       50
                                           :acl            {:owner {:principal "ADMIN"
                                                                    :type      "ROLE"}
                                                            :rules [{:principal user-id
                                                                     :right     "VIEW"
                                                                     :type      "USER"}]}}
                                :params   {:resource-name job/resource-url}}

          {{job-id :resource-id job-status :status} :body :as job-start-response} (crud/add request-job-creation)
          job-msg (str "starting " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to start deployment" 500 id)))
      (-> id
          (db/retrieve request)
          (a/can-modify? request)
          (assoc :state "STARTING")
          (db/edit request))
      (event-utils/create-event id job-msg (acl/default-acl (acl/current-authentication request)))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-url "stop"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-url "/" uuid)
          user-id (:identity (a/current-authentication request))
          request-job-creation {:identity std-crud/internal-identity
                                :body     {:action         "stop_deployment"
                                           :targetResource {:href id}
                                           :priority       60
                                           :acl            {:owner {:principal "ADMIN"
                                                                    :type      "ROLE"}
                                                            :rules [{:principal user-id
                                                                     :right     "VIEW"
                                                                     :type      "USER"}]}
                                           }
                                :params   {:resource-name job/resource-url}}

          {{job-id :resource-id job-status :status} :body :as job-stop-response} (crud/add request-job-creation)
          job-msg (str "stopping " id " with async " job-id)]
      (when (not= job-status 201)
        (throw (r/ex-response "unable to create async job to stop deployment" 500 id)))
      (-> id
          (db/retrieve request)
          (a/can-modify? request)
          (assoc :state "STOPPING")
          (db/edit request))
      (r/map-response job-msg 202 id job-id))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::deployment-spec/deployment))
