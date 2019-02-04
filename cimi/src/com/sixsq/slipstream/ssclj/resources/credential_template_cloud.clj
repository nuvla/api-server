(ns com.sixsq.slipstream.ssclj.resources.credential-template-cloud
  (:require
    [com.sixsq.slipstream.ssclj.resources.credential-template :as ct]
    [com.sixsq.slipstream.ssclj.resources.credential-template :as p]
    [com.sixsq.slipstream.ssclj.util.userparamsdesc :refer [slurp-cloud-cred-desc]]))


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


(def connector-template-description
  {:connector {:displayName "Connector"
               :category    "general"
               :description "connector cimi href"
               :type        "href"
               :mandatory   true
               :readOnly    false
               :order       12}})


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


(defn gen-description
  [cloud-service-type]
  (merge ct/CredentialTemplateDescription
         connector-template-description
         (slurp-cloud-cred-desc cloud-service-type)))


(defn register
  [cred-instance-map cloud-service-type]
  (p/register (gen-resource cred-instance-map cloud-service-type)
              (gen-description cloud-service-type)))
