# Design notes

## API resource namespace structure and initialisation

A typical API resource must be located under `sixsq.nuvla.server.resources` namespace 
and have the following structure

```clojure

(ns sixsq.nuvla.server.resources.nuvla-resource
  "Documentation"
  (:require
    [required.namespace :as n]))

;;
;; utilities
;;

(def ^:const resource-type (u/ns->type *ns*))


;;
;; CRUD operations
;;

(defn op1 [])

;;
;; initialization
;;

(defn add-document-one [] (println "adding one"))


(defn add-document-two [] (println "adding two"))


(defn initialize-data
  []
  (add-document-one)
  (add-document-two))


(defn initialize
  []
  
  ;; create index and mapping in DB
  (std-crud/initialize resource-type ::cep/resource)
  
  ;; register resource metadata
  (md/register resource-metadata)
  
  (initialize-data))
```

On the interface level with the public visibility, the resource

* must contain `initialize` function with nullary arity, and
* can contain `initialize-data` function with nullary arity (if required; see
  below).

**NOTE:** If the resource requires any data to be added to the corresponding DB
index (after it is properly initialised), this must be done via a separate
public function called `initialize-data`. This approach facilitates creation and
maintenance of the fixtures used in testing.

For a good example of the namespace defining an API resource see
[sixsq.nuvla.server.resources.cloud-entry-point](src/sixsq/nuvla/server/resources/cloud_entry_point.clj).
