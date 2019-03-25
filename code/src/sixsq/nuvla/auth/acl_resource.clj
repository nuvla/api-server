(ns sixsq.nuvla.auth.acl-resource
  (:require
    [clojure.string :as str]
    [clojure.set :as set]
    [sixsq.nuvla.server.util.response :as ru]
    [clojure.tools.logging :as log]))

(def rights-hierarchy (-> (make-hierarchy)

                          (derive ::edit-acl ::edit-data)
                          (derive ::edit-acl ::delete)
                          (derive ::edit-acl ::manage)
                          (derive ::edit-acl ::view-acl)

                          (derive ::edit-data ::edit-meta)
                          (derive ::edit-data ::view-data)

                          (derive ::edit-meta ::view-meta)

                          (derive ::view-acl ::view-data)
                          (derive ::view-data ::view-meta)))





(defn- add-rights-entry
  [m kw]
  (-> m
      (assoc kw kw)
      (assoc (-> kw name keyword) kw)))


(def rights-keywords
  (reduce add-rights-entry {} (cons ::edit-acl (ancestors rights-hierarchy ::edit-acl))))


(defn current-authentication
  "Extracts the current authentication (identity map) from the ring
   request.  Returns nil if there is no current identity."
  [{{:keys [current authentications]} :identity}]
  (or (get authentications current) {:identifier nil, :roles ["ANON"]}))


(defn extract-right
  "Given the identity map, this extracts the associated right.
  If the right does not apply, then nil is returned."
  [{:keys [identity roles] :as id-map} [right principals]]
  (when
    (some
      (set (remove nil? (conj roles identity "group/nuvla-anon")))
      (concat principals ["group/nuvla-admin" "ADMIN"]))
    (get rights-keywords right)))


(defn extract-rights
  "Returns a set containing all of the applicable rights from an ACL
   for the given identity map."
  [id-map {:keys [owners] :as acl}]
  (->> acl
       (update :edit-acl concat owners ["group/nuvla-admin"])
       (dissoc :owners)
       (map (partial extract-right id-map))
       (remove nil?)
       (set)))

(defn authorized-do?
  "Returns true if the ACL associated with the given resource permits the
   current user (in the request) the given action."
  [resource request action]
  (let [rights (extract-rights
                 (current-authentication request)
                 (:acl resource))
        action (get rights-keywords action)]
    (some #(isa? rights-hierarchy % action) rights)))

(defn authorized-view?
  "Returns true if the user can view the resource; returns false otherwise."
  [resource request]
  (authorized-do? resource request ::view))

(defn authorized-modify?
  "Returns true if the user can modify the resource; returns false otherwise."
  [resource request]
  (authorized-do? resource request ::modify))

(defn can-do?
  "Determines if the ACL associated with the given resource permits the
   current user (in the request) the given action.  If the action is
   allowed, then the resource itself is returned.  If the action is not
   allowed then an 'unauthorized' response map is thrown."
  [resource request action]
  (if (authorized-do? resource request action)
    resource
    (throw (ru/ex-unauthorized (:resource-id resource)))))

(defn can-modify?
  "Determines if the resource can be modified by the user in the request.
   Returns the request on success; throws an error ring response on
   failure."
  [resource request]
  (can-do? resource request ::modify))


(defn modifiable?
  "Predicate to determine if the given resource can be modified. Returns only
   true or false."
  [resource request]
  (try
    (can-modify? resource request)
    true
    (catch Exception _
      false)))


(defn can-view?
  "Determines if the resource can be modified by the user in the request.
   Returns the request on success; throws an error ring response on
   failure."
  [resource request]
  (can-do? resource request ::view))

(defn default-acl
  "Provides a default ACL based on the authentication information.
   The ACL will have the identity as the owner with no other ACL
   rules.  The only exception is if the user is in the ADMIN
   group, then the owner will be ADMIN.  If there is no identity
   then returns nil."
  [{:keys [identity roles]}]
  (if identity
    (if (contains? (set roles) "ADMIN")
      {:owner {:principal "ADMIN"
               :type      "ROLE"}}
      {:owner {:principal identity
               :type      "USER"}})))

(defn add-acl
  "Adds the default ACL to the given resource if an ACL doesn't already
   exist."
  [{:keys [acl] :as resource} request]
  (assoc
    resource
    :acl
    (or acl (default-acl (current-authentication request)))))

