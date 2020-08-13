(ns sixsq.nuvla.server.resources.subscription
  "
Resource for handling subscriptions.
"
  (:require
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]))


(def ^:const resource-type (u/ns->type *ns*))


(def ^:const collection-type (u/ns->collection-type *ns*))


(def ^:const resource-id (str resource-type "/subscription"))


(def collection-acl {:query ["group/nuvla-anon"]
                     :add   ["group/nuvla-admin"]})


