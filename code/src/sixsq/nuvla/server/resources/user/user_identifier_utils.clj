(ns sixsq.nuvla.server.resources.user.user-identifier-utils
  (:require
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.user-identifier :as user-identifier]))


(defn generate-identifier
  "Creates the identifier for a user-identifier resource of the form
   authn-method-instance:external-identifier. The instance may be nil."
  [authn-method instance external-identifier]
  (let [instance-string (if instance (str "-" instance) "")]
    (str (name authn-method) instance-string ":" external-identifier)))


(defn find-user-identifier
  "Returns the user-identifier resource associated with the given identifier.
   Returns nil if not found. Function never throws an exception."
  ([identifier]
   (try
     (->> identifier
          u/from-data-uuid
          (str user-identifier/resource-type "/")
          crud/retrieve-by-id-as-admin)
     (catch Exception _
       nil)))

  ([authn-method instance external-identifier]
   (find-user-identifier (generate-identifier authn-method instance external-identifier))))


(defn user-identifier-exists?
  "Returns true if a user-identifier resource with the given identifier
   exists. Returns false otherwise. Never throws an exception."
  [identifier]
  (->> identifier find-user-identifier boolean))


(defn user-identifier->user-id
  "Extracts the full user id (i.e. with 'user/' prefix) for the external
   identifier created from the arguments. Returns nil if the identifier doesn't
   exist. Never throws an exception."
  [authn-method instance external-identifier]
  (:parent (find-user-identifier authn-method instance external-identifier)))
