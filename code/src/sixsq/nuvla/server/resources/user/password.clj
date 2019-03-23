(ns sixsq.nuvla.server.resources.user.password
  (:require
    [sixsq.nuvla.server.resources.user :as p]))


(defn create-user-map
  "Transforms template into a user resource. Strips the method attribute and
   updates the resource-type."
  [{:keys [name description tags method] :as resource}]
  (cond-> {:resource-type p/resource-type
           :method        method
           :state         "NEW"}
          name (assoc :name name)
          description (assoc :description description)
          tags (assoc :tags tags)))
