(ns sixsq.nuvla.server.resources.configuration-nuvla
  "
Contains the core configuration of the Nuvla server. This resource must always
exist. If it isn't found when the server starts, then it will be created with
default values.
"
  (:require
    [clojure.string :as str]
    [clojure.tools.logging :as log]
    [environ.core :as env]
    [sixsq.nuvla.pricing.impl :as pricing-impl]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration :as p]
    [sixsq.nuvla.server.resources.configuration-template :as ct]
    [sixsq.nuvla.server.resources.configuration-template-nuvla :as tpl-nuvla]
    [sixsq.nuvla.server.resources.spec.configuration-template-nuvla :as ct-nuvla]
    [sixsq.nuvla.server.util.namespace-utils :as dyn]
    [sixsq.nuvla.server.util.response :as r]))


(def ^:const service "nuvla")


(def ^:const config-instance-url (str p/resource-type "/" service))


(def ^:const tpl-instance-url (str ct/resource-type "/" tpl-nuvla/service))


(def ^:dynamic *stripe-api-key* nil)

(def ^:dynamic *stripe-client-id* nil)


(def ^:dynamic *authorized-redirect-urls* nil)


(defn throw-stripe-not-configured
  []
  (when-not *stripe-api-key*
    (let [error-msg "server configuration for stripe is missing"]
      (throw (ex-info error-msg (r/map-response error-msg 500))))))


(defn authorized-url?
  [redirect-url]
  (or (nil? (seq *authorized-redirect-urls*))
      (->> *authorized-redirect-urls*
           (some (fn [authorized-redirect] (str/starts-with? redirect-url authorized-redirect)))
           boolean)))

(def ^:const error-msg-not-authorised-redirect-url "server configuration do not authorize following redirect-url: ")

(defn throw-is-not-authorised-redirect-url
  [redirect-url]
  (if (and redirect-url (not (authorized-url? redirect-url)))
    (let [error-msg (str error-msg-not-authorised-redirect-url redirect-url)]
      (throw (ex-info error-msg (r/map-response error-msg 400))))
    redirect-url))


;;
;; initialization: create initial service configuration if necessary
;;

(def create-template {:resource-type p/create-type
                      :template      {:href tpl-instance-url}})


(defn add-template
  []
  (std-crud/add-if-absent config-instance-url p/resource-type create-template))


(defn initialize-data
  []
  (add-template))


(defn initialize
  []

  (std-crud/initialize p/resource-type ::ct-nuvla/schema)

  (initialize-data)

  (let [nuvla-config (-> config-instance-url
                         crud/retrieve-by-id-as-admin)]
    (when-let [authorized-redirect-urls (:authorized-redirect-urls nuvla-config)]
      (alter-var-root #'*authorized-redirect-urls* (constantly authorized-redirect-urls)))
    (try
      (when-let [stripe-api-key (or (:stripe-api-key nuvla-config)
                                    (env/env :stripe-api-key))]
        (if-let [pricing-instance (dyn/load-ns "sixsq.nuvla.pricing.stripe.stripe")]
          (do
            (pricing-impl/set-impl! pricing-instance)
            (pricing-impl/set-api-key! stripe-api-key)
            (alter-var-root #'*stripe-api-key* (constantly stripe-api-key))
            (when-let [stripe-client-id (or (-> config-instance-url
                                                crud/retrieve-by-id-as-admin
                                                :stripe-client-id)
                                            (env/env :stripe-client-id))]
              (alter-var-root #'*stripe-client-id* (constantly stripe-client-id))))
          (log/error "Stripe-api-key configured but no princing implementation found!")))
      (catch Exception e
        (log/error (str "Exception when loading Stripe api-key/client-id: " e))))))


;;
;; multimethods for validation
;;

(def validate-fn (u/create-spec-validation-fn ::ct-nuvla/schema))


(defmethod p/validate-subtype service
  [resource]
  (validate-fn resource))


(def create-validate-fn (u/create-spec-validation-fn ::ct-nuvla/schema-create))


(defmethod p/create-validate-subtype service
  [resource]
  (create-validate-fn resource))


