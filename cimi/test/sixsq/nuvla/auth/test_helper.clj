(ns sixsq.nuvla.auth.test-helper
  (:refer-clojure :exclude [update])
  (:require
    [sixsq.nuvla.auth.internal :as ia]
    [sixsq.nuvla.db.es.binding :as esb]
    [sixsq.nuvla.db.es.utils :as esu]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.user :as ur]
    [sixsq.nuvla.server.resources.user-template :as ct]
    [sixsq.nuvla.server.resources.user-template-direct :as direct]
    [sixsq.nuvla.server.resources.user.user-identifier-utils :as uiu]))


(def rname ur/resource-url)


(def req-u-name "internal")


(def req-u-role "ADMIN")


(def req-template {:userTemplate
                   {:href   (str ct/resource-url "/" direct/registration-method)
                    :method "direct"}})


(def request-base {:identity     {:current         req-u-name
                                  :authentications {req-u-name {:roles    #{req-u-role}
                                                                :identity req-u-name}}}
                   :sixsq.slipstream.authn/claims
                                 {:username req-u-name
                                  :roles    #{req-u-role}}
                   :user-name    req-u-name
                   :params       {:resource-name rname}
                   :route-params {:resource-name rname}
                   :body         req-template})


(defn- user-request
  [{:keys [password state] :as user}]
  (->> (assoc user :password (ia/hash-password password)
                   :state (or state "ACTIVE"))
       (update-in request-base [:body :userTemplate] merge)))


(defn add-user-for-test!
  [user]
  (crud/add (user-request user)))

