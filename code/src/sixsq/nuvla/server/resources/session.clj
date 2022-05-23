(ns sixsq.nuvla.server.resources.session
  "
Users (clients) authenticate with the Nuvla server by referencing a
`session-template` resource (to identify the authentication method), providing
values for the associated parameters, and then creating a `session` resource
via the templated 'add' pattern.

A successful authentication attempt will return a token (as an HTTP cookie)
that must be used in subsequent interactions with the Nuvla server.

The detailed process consists of the following steps:

1. Browse the `session-template` resources to find the authentication method
   that you want to use. Unless you have a browser-based client, you will
   probably want to use either 'password' (username and password) or 'api-key'
   (API key and secret). **Use of API keys and secrets is preferred over the
   username and password for programmatic access.**

2. Prepare a 'create' JSON document that references the `session-template` you
   have chosen and provides the corresponding parameters (e.g. username and
   password for 'password').

3. Post the 'create' document to the `session` collection URL. The correct URL
   can be determined from the `cloud-entry-point` information.

4. On a successful authentication request, a token will be returned allowing
   access to the Nuvla resources as the authenticated user. **For convenience,
   this token is returned as an HTTP cookie.**

The authentication token (cookie) must be used in all subsequent requests to
the Nuvla server. The token (cookie) has a **limited lifetime** and you
must re-authenticate with the server when the token expires.

> NOTE: The search feature of `session` resources will only return the
`session` resource associated with your current session (or none at all if your
are not authenticated). This can be used to determine if you have an active
session and your associated identity and rights (e.g. groups).

An example document (named `create-password.json` below) for authenticating
via the 'password' (username and password) method.

```json
{
  \"template\" : {
                   \"href\" : \"session-template/password\",
                   \"username\" : \"your-username\",
                   \"password\" : \"your-password\"
                 }
}
```

A similar document for logging into with an API key/secret (named
`create-api-key.json`). Verify the name of the template on the server; the
administrator may have chosen a different name.

```json
{
  \"template\" : {
                   \"href\" : \"session-template/api-key\",
                   \"key\" : \"your-api-key\",
                   \"secret\" : \"your-api-secret\"
                 }
}
```

```shell
# Be sure to get the URL from the cloud entry point!
# The cookie options allow for automatic management of the
# Nuvla authentication token (cookie).
curl https://nuv.la/api/session \\
     -X POST \\
     -H 'content-type: application/json' \\
     -d @create-internal.json \\
     --cookie-jar ~/cookies -b ~/cookies -sS
```

On a successful authentication, the above command will return a 201 (created)
status, a 'set-cookie' header, and a 'location' header with the created
`session` resource.
"
  (:require
    [clojure.set :as set]
    [clojure.string :as str]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.cookies :as cookies]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.auth.utils.timestamp :as ts]
    [sixsq.nuvla.db.filter.parser :as parser]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.middleware.authn-info :as authn-info]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.email :as email]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.session :as session]
    [sixsq.nuvla.server.util.log :as log-util]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as r]
    [sixsq.nuvla.server.resources.group :as group]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const create-type (u/ns->create-type *ns*))


(def collection-acl {:query ["group/nuvla-anon"]
                     :add   ["group/nuvla-anon"]})


(def resource-metadata (gen-md/generate-metadata ::ns ::session/session))


;;
;; validate subclasses of sessions
;;

(defmulti validate-subtype
          :method)


(defmethod validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown Session type: '" (:method resource) "'") resource)))


(defmethod crud/validate resource-type
  [resource]
  (validate-subtype resource))

;;
;; validate create requests for subclasses of sessions
;;

(defn dispatch-on-authn-method [resource]
  (get-in resource [:template :method]))


(defmulti create-validate-subtype dispatch-on-authn-method)


(defmethod create-validate-subtype :default
  [resource]
  (throw (ex-info (str "unknown Session create type: "
                       (dispatch-on-authn-method resource) resource) resource)))


(defmethod crud/validate create-type
  [resource]
  (create-validate-subtype resource))


;;
;; multimethod for ACLs
;;

(defn create-acl
  [id]
  {:owners [id]})


(defmethod crud/add-acl resource-type
  [{:keys [id acl] :as resource} _request]
  (assoc
    resource
    :acl
    (or acl (create-acl id))))


(defn can-switch-group?
  [request]
  (-> (auth/current-groups request) seq boolean))


(defn dispatch-conversion
  "Dispatches on the Session authentication method for multimethods
   that take the resource and request as arguments."
  [resource _]
  (:method resource))


(defn standard-session-operations
  "Provides a list of the standard session operations, depending on the user's
   authentication and whether this is a Session or a SessionCollection."
  [{:keys [id resource-type] :as resource} request]
  (if (u/is-collection? resource-type)
    (when (a/can-add? resource request)
      [(u/operation-map id :add)])
    (cond-> []
            (a/can-delete? resource request) (conj (u/operation-map id :delete))
            (a/can-manage? resource request) (conj (u/action-map id :get-peers)
                                                   (u/action-map id :get-groups))
            (can-switch-group? request) (conj (u/action-map id :switch-group)))))


;; Sets the operations for the given resources.  This is a
;; multi-method because different types of session resources
;; may require different operations, for example, a 'validation'
;; callback.
(defmulti set-session-operations dispatch-conversion)


;; Default implementation adds the standard session operations
;; by ALWAYS replacing the :operations value.  If there are no
;; operations, the key is removed from the resource.
(defmethod set-session-operations :default
  [resource request]
  (let [ops (standard-session-operations resource request)]
    (cond-> (dissoc resource :operations)
            (seq ops) (assoc :operations ops))))


;; Just triggers the Session-level multimethod for adding operations
;; to the Session resource.
(defmethod crud/set-operations resource-type
  [resource request]
  (set-session-operations resource request))

;;
;; template processing
;;
;; The concrete implementation of this method MUST return a two-element
;; tuple containing a response fragment and the created session resource.
;; The response fragment will be merged with the 'add-impl' function
;; response and should be used to override the return status (e.g. to
;; instead provide a redirect) and to set a cookie header.
;;

(defmulti tpl->session dispatch-conversion)


;; All concrete session types MUST provide an implementation of this
;; multimethod. The default implementation will throw an 'internal
;; server error' exception.
;;

(defmethod tpl->session :default
  [_resource _request]
  [{:status 500, :message "invalid session resource implementation"} nil])


;;
;; CRUD operations
;;

(defn add-impl [{:keys [id body] :as request}]
  (a/throw-cannot-add collection-acl request)
  (db/add
    resource-type
    (-> body
        u/strip-service-attrs
        (assoc :id id)
        (assoc :resource-type resource-type)
        u/update-timestamps
        (u/set-created-by request)
        (crud/add-acl request)
        crud/validate)
    {}))


;; requires a SessionTemplate to create new Session
(defmethod crud/add resource-type
  [{:keys [body headers form-params] :as request}]
  (try
    (let [authn-info (auth/current-authentication request)
          body       (if (u/is-form? headers) (u/convert-form :template form-params) body)
          _          (-> body :redirect-url config-nuvla/throw-is-not-authorised-redirect-url)
          desc-attrs (u/select-desc-keys body)
          [cookie-header {:keys [id] :as body}]
          (-> body
              (assoc :resource-type create-type)
              (std-crud/resolve-hrefs authn-info true)
              (update-in [:template] merge desc-attrs)      ;; validate desc attrs
              (crud/validate)
              (:template)
              (tpl->session request))]
      (-> request
          (assoc :id id :body (merge body desc-attrs))
          add-impl
          (merge cookie-header)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(def retrieve-impl (std-crud/retrieve-fn resource-type))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(def delete-impl (std-crud/delete-fn resource-type))


(defn delete-cookie [{:keys [status] :as _response}]
  (if (= status 200)
    {:cookies (cookies/revoked-cookie authn-info/authn-cookie)}
    {}))


(defmethod crud/delete resource-type
  [request]
  (let [response (delete-impl request)
        cookies  (delete-cookie response)]
    (merge response cookies)))


(defn add-session-filter [{:keys [nuvla/authn] :as request}]
  (->> (or (:session authn) "")
       (format "id='%s'")
       (parser/parse-cimi-filter)
       (assoc-in request [:cimi-params :filter])))


(defn query-wrapper
  "wraps the standard query function to always include a filter based on the session"
  [query-fn]
  (fn [request]
    (query-fn (add-session-filter request))))


(def query-impl (query-wrapper (std-crud/query-fn resource-type collection-acl collection-type)))


(defmethod crud/query resource-type
  [request]
  (query-impl request))

;;
;; actions may be needed by certain authentication methods (notably external
;; methods like GitHub and OpenID Connect) to validate a given session
;;

(defmulti validate-callback dispatch-conversion)


(defmethod validate-callback :default
  [resource request]
  (log-util/log-and-throw 400 (str "error executing validation callback: '" (dispatch-conversion resource request) "'")))


(defmethod crud/do-action [resource-type "validate"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (validate-callback (crud/retrieve-by-id-as-admin id) request))
    (catch Exception e
      (or (ex-data e)
          (throw e)))))


(defn throw-switch-group-not-authorized
  [resource {{:keys [claim]} :body :as request}]
  (let [groups  (auth/current-groups request)
        user-id (auth/current-user-id request)]
    (if (or (= claim user-id)
            (contains? groups claim))
      resource
      (throw
        (r/ex-response
          (format "Switch group cannot be done to requested group: %s!" claim) 403)))))


(def edit-impl (std-crud/edit-fn resource-type))

(defn update-cookie-session
  [{:keys [id user roles active-claim client-ip] :as session}
   {headers :headers {:keys [claim]} :body :as request}]
  (let [updated-claims (-> roles
                           authn-info/split-claims
                           (disj (or active-claim user))
                           (conj claim))
        updated-roles  (->> updated-claims sort (str/join " "))
        cookie-info    (cookies/create-cookie-info user
                                                   :session-id id
                                                   :claims updated-claims
                                                   :active-claim claim
                                                   :headers headers
                                                   :client-ip client-ip)
        cookie         (cookies/create-cookie cookie-info)
        expires        (ts/rfc822->iso8601 (:expires cookie))
        session        (assoc session :expiry expires
                                      :active-claim claim
                                      :groups (:groups cookie-info)
                                      :roles updated-roles)]
    (-> request
        (assoc :body session)
        (edit-impl)
        (assoc-in [:cookies authn-info/authn-cookie] cookie))))


(defn retrieve-session
  [{{uuid :uuid} :params :as request}]
  (db/retrieve (str resource-type "/" uuid) request))


(defn query-group
  [filter-str]
  (second (crud/query-as-admin
            group/resource-type
            {:cimi-params {:filter (parser/parse-cimi-filter
                                     filter-str)
                           :last   10000
                           :select ["id" "name" "description" "parents"]}})))


(defn group-hierarchy [{:keys [id] :as group} subgroups]
  (let [childs (filter (comp #{id} last :parents) subgroups)]
    (cond-> (select-keys group [:id :name :description :children])
            (seq childs) (assoc :children
                                (map #(group-hierarchy % subgroups) childs)))))


(defmethod crud/do-action [resource-type "get-groups"]
  [request]
  (try
    (let [{:keys [user]} (-> request
                             retrieve-session
                             (a/throw-cannot-manage request))
          root-groups  (query-group (str "users='" user "'"))
          subgroups    (when (seq root-groups)
                         (->> root-groups
                              (map #(str "parents='" (:id %) "'"))
                              (str/join " or ")
                              query-group))
          all-grps-ids (set/union (set (map :id subgroups))
                                  (set (map :id root-groups)))]
      (->> root-groups
           (remove (comp all-grps-ids last :parents))
           (map #(assoc % :parents [:root]))
           (concat subgroups)
           (sort-by (juxt :name :id))
           (group-hierarchy {:id :root :children []})
           :children
           r/json-response))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "switch-group"]
  [{{uuid :uuid} :params :as request}]
  (try
    (let [id (str resource-type "/" uuid)]
      (-> (db/retrieve id request)
          (throw-switch-group-not-authorized request)
          (a/throw-cannot-edit request)
          (update-cookie-session request)))
    (catch Exception e
      (or (ex-data e) (throw e)))))


(defmethod crud/do-action [resource-type "get-peers"]
  [request]
  (try
    (-> request
        retrieve-session
        (a/throw-cannot-manage request))
    (let [user-id       (auth/current-user-id request)
          is-admin?     (a/is-admin? (auth/current-authentication request))
          peers-ids     (when-not is-admin?
                          (->> (cookies/collect-groups-for-user
                                 user-id :with-users? true)
                               (map :users)
                               (reduce set/union #{})
                               seq))
          filter-emails (if peers-ids
                          (->> peers-ids
                               (map #(format "parent='%s'" %))
                               (str/join " or ")
                               (format "(%s) and validated=true"))
                          (when is-admin? "validated=true"))
          peers         (when filter-emails
                          (->> {:cimi-params {:filter (parser/parse-cimi-filter filter-emails)
                                              :select ["id", "address", "parent"]
                                              :last   10000}}
                               (crud/query-as-admin email/resource-type)
                               second
                               (map (juxt :parent :address))
                               (into {})))]
      (r/json-response (or peers {})))
    (catch Exception e
      (or (ex-data e) (throw e)))))


;;
;; initialization: no schema for this parent resource
;;

(defn initialize
  []
  (std-crud/initialize resource-type nil)
  (md/register resource-metadata))
