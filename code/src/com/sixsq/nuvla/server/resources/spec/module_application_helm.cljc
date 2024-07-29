(ns com.sixsq.nuvla.server.resources.spec.module-application-helm
  (:require
    [clojure.spec.alpha :as s]
    [com.sixsq.nuvla.server.resources.spec.common :as common]
    [com.sixsq.nuvla.server.resources.spec.container :as container]
    [com.sixsq.nuvla.server.resources.spec.core :as core]
    [com.sixsq.nuvla.server.resources.spec.credential-template :as cred-spec]
    [com.sixsq.nuvla.server.resources.spec.deployment :as deployment]
    [com.sixsq.nuvla.server.resources.spec.module-application :as module-app]
    [com.sixsq.nuvla.server.resources.spec.module-component :as module-component]
    [com.sixsq.nuvla.server.util.spec :as su]
    [spec-tools.core :as st]))


(s/def ::helm-repo-url
  (assoc (st/spec ::container/infrastructure-service-id)
    :name "helm-repo-url"
    :json-schema/description "Helm repo URL infrastructure service"))


(s/def ::helm-chart-name
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "helm-chart-name"
             :json-schema/description "Helm chart name")))


(s/def ::helm-absolute-url
  (-> (st/spec ::core/url)
      (assoc :name "helm-absolute-url"
             :json-schema/description "Helm chart absolute URL")))


(s/def ::helm-chart-version
  (-> (st/spec ::core/nonblank-string)
      (assoc :name "helm-chart-version"
             :json-schema/description "Helm chart version to deploy, latest (if empty)")))


(s/def ::helm-repo-cred
  (assoc cred-spec/credential-id-spec
    :name "helm-repo-cred"
    :json-schema/description "Helm repo credential"))


(s/def ::helm-chart-values
  (-> (st/spec string?)
      (assoc :name "helm-chart-values"
             :json-schema/description "Helm values in YAML format")))


(def module-application-helm-keys-spec (su/merge-keys-specs
                                         [common/common-attrs
                                          {:req-un [::module-component/author]
                                           :opt-un [::module-component/commit
                                                    ::module-component/urls
                                                    ::module-component/output-parameters
                                                    ::container/environmental-variables
                                                    ::container/private-registries
                                                    ::deployment/registries-credentials
                                                    ::module-app/files
                                                    ::module-app/requires-user-rights

                                                    ;; mandatory Helm fields
                                                    ::helm-repo-url
                                                    ::helm-chart-name
                                                    ::helm-absolute-url

                                                    ;; optional Helm fields
                                                    ::helm-chart-version
                                                    ::helm-repo-cred
                                                    ::helm-chart-values]}]))


(s/def ::schema (su/only-keys-maps module-application-helm-keys-spec))
