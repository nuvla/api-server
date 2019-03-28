(ns sixsq.nuvla.auth.acl-resource
  (:require
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.server.util.response :as ru]))

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


(defn extract-right
  "Given the identity map, this extracts the associated right.
  If the right does not apply, then nil is returned."
  [{:keys [claims]} [right principals]]
  (let [principals-with-admin (conj principals "group/nuvla-admin")]
    (when claims
      (when (some claims principals-with-admin)
       (get rights-keywords right)))))


(defn extract-rights
  "Returns a set containing all of the applicable rights from an ACL
   for the given identity map."
  [authn-info {:keys [owners] :as acl}]
  (let [acl-updated (-> acl
                        (update :edit-acl concat owners ["group/nuvla-admin"])
                        (dissoc :owners))]

    (->> acl-updated
         (map (partial extract-right authn-info))
         (remove nil?)
         (set))))


(defn authorized-do?
  "Returns true if the ACL associated with the given resource permits the
   current user (in the request) the given action."
  [resource request action]
  (let [rights (extract-rights
                 (auth/current-authentication request)
                 (:acl resource))
        action (get rights-keywords action)]
    (some #(isa? rights-hierarchy % action) rights)))


;;TODO ACL complete the list with all rights

(defn authorized-view-acl?
  "Returns true if the user has the view-acl right on the resource; returns false otherwise."
  [resource request]
  (authorized-do? resource request ::view-acl))


(defn authorized-edit-acl?
  "Returns true if the user has the edit-acl right on the resource; returns false otherwise."
  [resource request]
  (authorized-do? resource request ::edit-acl))


(defn can-do?
  "Determines if the ACL associated with the given resource permits the
   current user (in the request) the given action.  If the action is
   allowed, then the resource itself is returned.  If the action is not
   allowed then an 'unauthorized' response map is thrown."
  [resource request action]
  (if (authorized-do? resource request action)
    resource
    (throw (ru/ex-unauthorized (:resource-id resource)))))


(defn can-edit-acl?
  "Determines if the resource can be modified by the user in the request.
   Returns the request on success; throws an error ring response on
   failure."
  [resource request]
  (can-do? resource request ::edit-acl))


(defn modifiable?
  "Predicate to determine if the given resource can be modified. Returns only
   true or false."
  [resource request]
  (try
    (can-edit-acl? resource request)
    true
    (catch Exception _
      false)))


(defn can-view-acl?
  "Determines if the resource can be modified by the user in the request.
   Returns the request on success; throws an error ring response on
   failure."
  [resource request]
  (can-do? resource request ::view-acl))


(defn default-acl
  "Provides a default ACL based on the authentication information.
   The ACL will have the identity as the owner with no other ACL
   rules.  The only exception is if the user is in the group/nuvla-admin,
   then the owner will be group/nuvla-admin.  If there is no identity
   then returns nil."
  [{:keys [user-id claims]}]
  (when user-id
    (if ((set claims) "group/nuvla-admin")
      {:owners ["group/nuvla-admin"]}
      {:owners [user-id]})))


(defn add-acl
  "Adds the default ACL to the given resource if an ACL doesn't already
   exist."
  [{:keys [acl] :as resource} request]
  (assoc
    resource
    :acl
    (or acl (default-acl (auth/current-authentication request)))))
