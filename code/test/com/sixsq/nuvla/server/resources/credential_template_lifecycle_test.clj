(ns com.sixsq.nuvla.server.resources.credential-template-lifecycle-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.sixsq.nuvla.server.app.params :as p]
    [com.sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [com.sixsq.nuvla.server.resources.common.crud :as crud]
    [com.sixsq.nuvla.server.resources.common.utils :as u]
    [com.sixsq.nuvla.server.resources.credential-template :as ct]
    [com.sixsq.nuvla.server.resources.credential-template-api-key :as akey]
    [com.sixsq.nuvla.server.resources.credential-template-gpg-key :as gpgkey]
    [com.sixsq.nuvla.server.resources.credential-template-hashed-password :as hashed-password]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-helm-repo :as srvc-helm-repo]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-kubernetes :as srvc-kubernetes]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-minio :as srvc-minio]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-registry :as srvc-registry]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-swarm :as srvc-swarm]
    [com.sixsq.nuvla.server.resources.credential-template-infrastructure-service-vpn-customer
     :as srvc-vpn]
    [com.sixsq.nuvla.server.resources.credential-template-ssh-key :as sshkey]
    [com.sixsq.nuvla.server.resources.credential-template-swarm-token :as swarm-token]
    [com.sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [com.sixsq.nuvla.server.util.metadata-test-utils :as mdtu]
    [peridot.core :refer [content-type header request session]]))


(use-fixtures :once ltu/with-test-server-fixture)


(def base-uri (str p/service-context ct/resource-type))


(deftest check-retrieve-by-id
  (doseq [registration-method [akey/method
                               gpgkey/method
                               sshkey/method
                               hashed-password/method
                               srvc-minio/method
                               srvc-swarm/method
                               swarm-token/method]]
    (let [id  (str ct/resource-type "/" registration-method)
          doc (crud/retrieve-by-id id {:nuvla/authn
                                       {:user-id      "user/alpha"
                                        :active-claim "user/alpha"
                                        :claims       #{"group/nuvla-user" "group/nuvla-anon"}}})]
      (is (= id (:id doc))))))


(deftest check-metadata
  (mdtu/check-metadata-exists ct/resource-type)

  (doseq [resource-url [akey/resource-url
                        gpgkey/resource-url
                        sshkey/resource-url
                        hashed-password/resource-url
                        srvc-minio/credential-subtype
                        srvc-swarm/credential-subtype
                        swarm-token/credential-subtype]]
    (mdtu/check-metadata-exists (str ct/resource-type "-" resource-url)
                                (str ct/resource-type "-" resource-url "-create"))))


;; check that all templates are visible as normal user
(deftest lifecycle-admin
  (let [session-user (-> (session (ltu/ring-app))
                         (content-type "application/json")
                         (header authn-info-header "user/jane user/jane group/nuvla-user group/nuvla-anon"))
        entries      (-> session-user
                         (request base-uri)
                         (ltu/body->edn)
                         (ltu/is-status 200)
                         (ltu/is-resource-uri ct/collection-type)
                         (ltu/is-count pos?)
                         (ltu/is-operation-absent :add)
                         (ltu/is-operation-absent :delete)
                         (ltu/is-operation-absent :edit)
                         (ltu/entries))
        ids          (set (map :id entries))
        methods      (set (map :method entries))
        types        (set (map :subtype entries))]
    (is (= #{(str ct/resource-type "/" akey/method)
             (str ct/resource-type "/" gpgkey/method)
             (str ct/resource-type "/" sshkey/method)
             (str ct/resource-type "/" hashed-password/method)
             (str ct/resource-type "/" srvc-minio/method)
             (str ct/resource-type "/" srvc-swarm/method)
             (str ct/resource-type "/" srvc-kubernetes/method)
             (str ct/resource-type "/" swarm-token/method)
             (str ct/resource-type "/" srvc-vpn/method)
             (str ct/resource-type "/" srvc-registry/method)
             (str ct/resource-type "/" srvc-helm-repo/method)}
           ids))
    (is (= #{akey/method
             gpgkey/method
             sshkey/method
             hashed-password/method
             srvc-minio/method
             srvc-registry/method
             srvc-helm-repo/method
             srvc-swarm/method
             srvc-kubernetes/method
             srvc-vpn/method
             swarm-token/method} methods))
    (is (= #{akey/credential-subtype
             gpgkey/credential-subtype
             sshkey/credential-subtype
             hashed-password/credential-subtype
             srvc-minio/credential-subtype
             srvc-registry/credential-subtype
             srvc-helm-repo/credential-subtype
             srvc-swarm/credential-subtype
             srvc-kubernetes/credential-subtype
             srvc-vpn/credential-subtype
             swarm-token/credential-subtype} types))

    (doseq [entry entries]
      (let [ops        (ltu/operations->map entry)
            entry-url  (str p/service-context (:id entry))

            entry-resp (-> session-user
                           (request entry-url)
                           (ltu/is-status 200)
                           (ltu/body->edn))

            entry-body (get-in entry-resp [:response :body])]
        (is (nil? (get ops (name :add))))
        (is (nil? (get ops (name :edit))))
        (is (nil? (get ops (name :delete))))

        (is (crud/validate entry-body))))))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id ct/resource-type))]
    (ltu/verify-405-status [[base-uri :post]
                            [base-uri :delete]
                            [resource-uri :put]
                            [resource-uri :post]
                            [resource-uri :delete]])))
