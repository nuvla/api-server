(ns sixsq.nuvla.auth.acl-resource
  (:require
    [clojure.set :as set]
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


(def collection-rights
  [::query ::add])


;; provides mappings between the namespaced keywords and themselves,
;; as well as the un-namespaced keywords and the namespaced ones.
(def rights-keywords
  (reduce add-rights-entry {} (concat collection-rights [::edit-acl] (ancestors rights-hierarchy ::edit-acl))))


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


(defn extract-all-rights
  "Returns a set containing all of the applicable rights from an ACL for the
   given identity map and all rights implied by the explicit ones."
  [authn-info acl]
  (let [explicit-rights (extract-rights authn-info acl)
        implicit-rights (mapcat (partial ancestors rights-hierarchy) explicit-rights)]
    (set (concat explicit-rights implicit-rights))))


(defn authorized-do?
  "Returns true if the ACL associated with the given resource permits the
   current user (in the request) the given action."
  [resource request action]
  (let [rights (extract-rights
                 (auth/current-authentication request)
                 (:acl resource))
        action (get rights-keywords action)]
    (some #(isa? rights-hierarchy % action) rights)))


(defn can-do?
  "Determines if the ACL associated with the given resource permits the
   current user (in the request) the given action.  If the action is
   allowed, then the resource itself is returned.  If the action is not
   allowed then an 'unauthorized' response map is thrown."
  [resource request action]
  (if (authorized-do? resource request action)
    resource
    (throw (ru/ex-unauthorized (:resource-id resource)))))


;; FIXME: Remove this.
(defn can-edit-acl?
  "Determines if the resource can be modified by the user in the request.
   Returns the request on success; throws an error ring response on
   failure."
  [resource request]
  (can-do? resource request ::edit-acl))


(defn has-rights?
  "Based on the rights derived from the authentication information and the
   acl, this function returns true if the given `right` is allowed."
  [required-rights {:keys [acl] :as resource} request]
  (let [rights (extract-all-rights (auth/current-authentication request) acl)]
    (boolean (seq (set/intersection required-rights rights)))))


(def can-delete? (partial has-rights? #{::delete}))


(def can-add? (partial has-rights? #{::add}))


(def can-manage? (partial has-rights? #{::manage}))


(def can-edit? (partial has-rights? #{::edit-meta ::edit-data ::edit-acl}))


(def can-view? (partial has-rights? #{::view-meta ::view-data ::view-acl}))


(def ^:const metadata-keys #{:id
                             :resource-type
                             :created
                             :updated
                             :name
                             :description
                             :tags
                             :parent
                             :resource-metadata
                             :operations})


(defn editable-keys
  "Based on the rights, this function returns a reduced set of keys that are
   'editable'. The arguments can either be sequences or sets."
  [ks rights]
  (let [key-set (set ks)
        rights-set (set rights)]
    (cond
      (rights-set ::edit-acl) key-set                       ;; no-op, all keys are editable
      (rights-set ::edit-data) (disj key-set :acl)
      (rights-set ::edit-meta) (set/intersection key-set metadata-keys)
      :else #{})))


(defn select-viewable-keys
  "Based on the rights derived from the authentication information and the
   acl, this function returns a reduced resource containing only keys that are
   'viewable'. Returns nil if the resource is not viewable at all."
  [{:keys [acl] :as resource} request]
  (let [rights-set (extract-all-rights (auth/current-authentication request) acl)]
    (cond
      (rights-set ::view-acl) resource                      ;; no-op, all keys are viewable
      (rights-set ::view-data) (dissoc resource :acl)
      (rights-set ::view-meta) (select-keys resource metadata-keys)
      :else nil)))


(defn throw-without-rights
  "Will throw an error ring response if the user identified in the request
   does not have any of the required rights; it returns the resource otherwise."
  [required-rights {:keys [acl] :as resource} request]
  (let [rights (extract-all-rights (auth/current-authentication request) acl)]
    (if (seq (set/intersection required-rights rights))
      resource
      (throw (ru/ex-unauthorized (:id resource))))))


(def throw-cannot-delete (partial throw-without-rights #{::delete}))


(def throw-cannot-edit (partial throw-without-rights #{::edit-meta ::edit-data ::edit-acl}))


(def throw-cannot-view (partial throw-without-rights #{::view-meta ::view-data ::view-acl}))


(def throw-cannot-view-data (partial throw-without-rights #{::view-data}))


(def throw-cannot-manage (partial throw-without-rights #{::manage}))


(defn throw-cannot-query
  "Will throw an error ring response if the user identified in the request
   cannot query the given collection; it returns the resource otherwise."
  [collection-acl request]
  (can-do? {:acl collection-acl} request ::query))


(defn throw-cannot-add
  "Will throw an error ring response if the user identified in the request
   cannot add a resource to the given collection; it returns the resource
   otherwise."
  [collection-acl request]
  (can-do? {:acl collection-acl} request ::add))


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
