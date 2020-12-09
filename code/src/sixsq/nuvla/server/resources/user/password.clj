(ns sixsq.nuvla.server.resources.user.password)


(defn create-user-map
  "Transforms template into a user resource. Strips the method attribute and
   updates the resource-type."
  [{:keys [name description tags method username email] :as resource}]
  (let [name-attr (or name username email)]
    (cond-> {:resource-type "user"
             :method        method
             :state         "NEW"}
            name-attr (assoc :name name-attr)
            description (assoc :description description)
            tags (assoc :tags tags))))
