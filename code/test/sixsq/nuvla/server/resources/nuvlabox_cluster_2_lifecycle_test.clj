(ns sixsq.nuvla.server.resources.nuvlabox-cluster-2-lifecycle-test
  (:require
    [clojure.data.json :as json]
    [clojure.string :as str]
    [clojure.test :refer [deftest use-fixtures]]
    [peridot.core :refer [content-type header request session]]
    [ring.util.codec :as rc]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.middleware.authn-info :refer [authn-info-header]]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.configuration-nuvla :as config-nuvla]
    [sixsq.nuvla.server.resources.lifecycle-test-utils :as ltu]
    [sixsq.nuvla.server.resources.nuvlabox :as nb]
    [clojure.pprint :refer [pprint]]
    [sixsq.nuvla.server.resources.nuvlabox-status :as nb-status]
    [sixsq.nuvla.server.resources.nuvlabox-cluster :as nb-cluster]
    [sixsq.nuvla.server.resources.nuvlabox-cluster-2 :as nb-cluster-2]
    [sixsq.nuvla.server.util.metadata-test-utils :as mdtu]))


(use-fixtures :each ltu/with-test-server-fixture)


(def base-uri (str p/service-context nb-cluster/resource-type))


(def nuvlabox-base-uri (str p/service-context nb/resource-type))

(def nuvlabox-status-base-uri (str p/service-context nb-status/resource-type))

(def timestamp "1964-08-25T10:00:00Z")


(def nuvlabox-id "nuvlabox/some-random-uuid")


(def nuvlabox-owner "user/alpha")

(def user-beta "user/beta")


(def valid-nuvlabox {:owner nuvlabox-owner})

(def valid-nuvlabox-status {:node-id "abcd1234"
                            :version 2
                            :status                "OPERATIONAL"
                            :cluster-node-role     "worker"})

(def valid-acl {:owners    [nuvlabox-owner]})

(def valid-cluster {:id            (str nb-cluster/resource-type "/uuid")
                    :resource-type nb-cluster/resource-type
                    :name          "cluster 1234abcd"
                    :description   "a NB cluster with X nodes"

                    :version       2

                    :acl           valid-acl

                    :cluster-id    "1234abcdcluster"
                    :managers      ["abcd1234"]
                    :orchestrator  "swarm"})


(deftest check-metadata
  (mdtu/check-metadata-exists nb-cluster/resource-type
    (str nb-cluster/resource-type "-" nb-cluster-2/schema-version)))


(deftest lifecycle

  (binding [config-nuvla/*stripe-api-key* nil]
    (let [session       (-> (ltu/ring-app)
                           session
                           (content-type "application/json"))
         session-admin (header session authn-info-header "group/nuvla-admin group/nuvla-admin group/nuvla-user group/nuvla-anon")
         session-user  (header session authn-info-header (str user-beta " " user-beta " group/nuvla-user group/nuvla-anon"))
         session-owner (header session authn-info-header (str nuvlabox-owner " " nuvlabox-owner " group/nuvla-user group/nuvla-anon"))
         session-anon  (header session authn-info-header "user/unknown user/unknown group/nuvla-anon")

         nuvlabox-id   (-> session-owner
                           (request nuvlabox-base-uri
                                    :request-method :post
                                    :body (json/write-str valid-nuvlabox))
                           (ltu/body->edn)
                           (ltu/is-status 201)
                           (ltu/location))

          nuvlabox-status-id  (-> session-admin
                                (request nuvlabox-status-base-uri
                                  :request-method :post
                                  :body (json/write-str (assoc valid-nuvlabox-status :parent nuvlabox-id
                                                                                     :acl {:owners    ["group/nuvla-admin"]
                                                                                           :edit-data [nuvlabox-id]})))
                                (ltu/body->edn)
                                (ltu/is-status 201)
                                (ltu/location))

         session-nb    (header session authn-info-header (str nuvlabox-id " " nuvlabox-id " group/nuvla-user group/nuvla-anon"))]

     ;; anonymous users cannot create a nuvlabox-cluster resource
     (-> session-anon
         (request base-uri
                  :request-method :post
                  :body (json/write-str valid-cluster))
         (ltu/body->edn)
         (ltu/is-status 403))

     ;; admin/nuvlabox users can create a nuvlabox-cluster resource
     ;; use the default ACL
     (when-let [cluster-url (-> session-nb
                                   (request base-uri
                                            :request-method :post
                                            :body (json/write-str valid-cluster))
                                   (ltu/body->edn)
                                   (ltu/is-status 201)
                                   (ltu/location-url))]

       ;; other users can't see the cluster
       (-> session-user
           (request cluster-url)
           (ltu/body->edn)
           (ltu/is-status 403))

       ;; owners can see the cluster
       (-> session-owner
           (request cluster-url)
           (ltu/body->edn)
           (ltu/is-status 200))

       ;; admin can see the cluster
       (-> session-admin
         (request cluster-url)
         (ltu/body->edn)
         (ltu/is-status 200))

       ;; nuvlabox user cannot update nuvlabox-cluster (only via commissioning
       (-> session-nb
           (request cluster-url
                    :request-method :put
                    :body (json/write-str {:name "new cluster name"}))
           (ltu/body->edn)
           (ltu/is-status 403))

       ;; nuvlabox owner identity cannot delete the cluster
       (-> session-owner
           (request cluster-url
                    :request-method :delete)
           (ltu/body->edn)
           (ltu/is-status 403))

       ;; share nuvlabox with user beta
       (-> session-owner
           (request (str p/service-context nuvlabox-id)
                    :request-method :put
                    :body (json/write-str {:acl {:owners   ["group/nuvla-admin"]
                                                 :view-acl [nuvlabox-owner user-beta]}}))
           (ltu/body->edn)
           (ltu/is-status 200))

       ;; now user beta can also see the cluster
       (-> session-admin
           (request cluster-url)
           (ltu/body->edn)
           (ltu/is-status 200))

       ;; nuvlabox cannot delete the cluster
       (-> session-nb
           (request cluster-url
                    :request-method :delete)
           (ltu/body->edn)
           (ltu/is-status 403))

       ;; only admin can delete
       (-> session-admin
         (request cluster-url
           :request-method :delete)
         (ltu/body->edn)
         (ltu/is-status 200)))


     ;(when-let [cluster-url (-> session-nb
     ;                              (request base-uri
     ;                                       :request-method :post
     ;                                       :body (json/write-str valid-cluster))
     ;                              (ltu/body->edn)
     ;                              (ltu/is-status 201)
     ;                              (ltu/location-url))]
     ;
     ;  ;; nuvlabox can delete the cluster
     ;  (-> session-admin
     ;      (request cluster-url
     ;               :request-method :delete)
     ;      (ltu/body->edn)
     ;      (ltu/is-status 200)))
     )))


(deftest bad-methods
  (let [resource-uri (str p/service-context (u/new-resource-id nb-cluster/resource-type))]
    (ltu/verify-405-status [[base-uri :options]
                            [base-uri :delete]
                            [resource-uri :options]
                            [resource-uri :post]])))
