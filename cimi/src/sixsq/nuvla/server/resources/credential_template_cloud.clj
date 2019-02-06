(ns sixsq.nuvla.server.resources.credential-template-cloud
  (:require
    [sixsq.nuvla.server.resources.credential-template :as p]))


(def ^:const resource-acl-default {:owner {:principal "ADMIN"
                                           :type      "ROLE"}
                                   :rules [{:principal "USER"
                                            :type      "ROLE"
                                            :right     "VIEW"}]})


(def ^:const resource-base
  {:name        "User cloud credentials store"
   :description "Stores user cloud credentials"
   :connector   {:href ""}
   :key         ""
   :secret      ""
   :quota       20
   :acl         resource-acl-default})


(defn cred-type
  [cloud-service-type]
  (str "cloud-cred-" cloud-service-type))


(defn cred-method
  [cloud-service-type]
  (str "store-cloud-cred-" cloud-service-type))


(defn gen-resource
  [cred-instance-map cloud-service-type]
  (merge resource-base
         {:name        (str "User cloud credentials store for " cloud-service-type)
          :description (str "Stores user cloud credentials for cloud " cloud-service-type)
          :type        (cred-type cloud-service-type)
          :method      (cred-method cloud-service-type)}
         cred-instance-map))


(defn register
  [cred-instance-map cloud-service-type]
  (p/register (gen-resource cred-instance-map cloud-service-type)))
