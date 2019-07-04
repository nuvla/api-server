(ns sixsq.nuvla.server.resources.cloud-entry-point
  "
The `cloud-entry-point` provides a list of all resource (document) collections
available from the Nuvla server. See the general information about the service
below. Detailed metadata for each collection (and for some resource templates)
can be found in the resource-metadata collection.

# CIMI API

The [Cloud Infrastructure Management
Interface](https://www.dmtf.org/sites/default/files/standards/documents/DSP0263_2.0.0.pdf)
(CIMI) specification from DMTF describes a uniform, extensible, RESTful (HTTP)
API for the management of cloud resources.

The Nuvla API has been inspired by the CIMI specification, with changes and
additions based on experience from a previous implementation.

## Autodiscovery

The Nuvla API has been designed to allow for automatic discovery of the
supported resources and management operations.

### Cloud Entry Point

The `cloud-entry-point` lists all available resources and provides a URL,
relative to the `base-uri` for accessing each collection.

```shell
curl https://nuvla.io/api/cloud-entry-point
```

```json
{
  \"id\" : \"cloud-entry-point\",
  \"resource-type\" : \"http://schemas.dmtf.org/cimi/2/cloud-entry-point\",
  \"created\" : \"2016-06-21T17:31:14.950Z\",
  \"updated\" : \"2016-06-21T17:31:14.950Z\",

  \"base-uri\" : \"https://nuv.la/api/\",

  \"collections\" : {
    \"email\" : {
      \"href\" : \"email\"
    },
    \"user-identifier\" : {
      \"href\" : \"user-identifier\"
    },
    ...
  }
}
```

> WARNING: Although Nuvla maintains consistent naming throughout the API,
assumptions about the URL naming must not be made by clients. Clients must use
the cloud-entry-point to discover the correct URLs for managed resources.

### Operations

If the user is authorized to perform management operations on a resource, the
resource will contain an `operations` key. The value of the key will contain a
list of actions (e.g. `add`, `edit`, `delete`, `start`) along with the URL to
use to execute the action.

The HTTP methods to use for the CIMI `add`, `edit`, and `delete` operations
are POST, PUT, and DELETE, respectively. All other operations use the HTTP POST
method.

## Resource Management (CRUD)

Nuvla adopts the CIMI standard patterns for all of the usual database actions:
Search (or Query), Create, Read, Update, and Delete (SCRUD), although the
specification uses 'Add' for 'Create' and 'Edit' for 'Update'.

**See Section 4.2.1 of the CIMI specification for detailed descriptions of
these patterns.** Only differences from the standard patterns are documented
here.

### HTTP Methods

The following table shows the mapping between the resource management
action and the HTTP method to be used.

Action | HTTP Method | URL
------ | ----------- | ---
Search | `GET` or `PUT` | resource collection
Add (create) | `POST` | resource collection
Read | `GET` | resource
Edit (update) | `PUT` | resource
Delete | `DELETE` | resource
Other | `POST` | operation URL

> NOTE: Specialized actions can appear in the operations section of a
resource. Any non-standard operation will use the POST method for the request.
The parameters (if any) will depend on the operation and should be documented
in the resource-metadata.

### Add Pattern Variations

Note that there are two CIMI add (create) patterns:

 * **Direct creation** that takes uses the new resource's content
   directly in the creation request, and

 * **Templated creation** that uses a template to create the resource.

In the `cloud-entry-point`, **any resource that has a corresponding template
resource will use the templated add pattern**. For example, the `session`
resource will use the templated add pattern because there is also a
`session-template` resource.

## Resource Selection

The CIMI specification provides advanced features for selecting resources when
searching collections, including paging (CIMI Section 4.1.6.2), subsetting
(4.1.6.3), sorting (4.1.6.6), and filtering (4.1.6.1).

All the resource selection parameters are specified as HTTP query parameters.
These are specified directly within the URL when using the HTTP GET method.
They are specified in a body with the media type of
`application/x-www-form-urlencoded` when using the PUT method. (Note that use
of PUT for searches is a Nuvla extension to the CIMI standard that allows for
long, complex filters.)

All the CIMI query parameters are prefixed with a dollar sign ($). This was an
unfortunate choice because it signals variable interpolation in most shells and
thus complicates command-line access to the API. To avoid this issue, **the
Nuvla API drops the dollar sign from query parameters.** For example, use
`first` instead of the CIMI `$first`.

### Ordering

The results can be ordered by the values of fields within the resources. The
general form of a query with ordering is:

`orderby=attributeName[:asc|:desc], ...`

The ascending (:asc, default) or descending (:desc) field is optional. The
sorting is done using the natural order of the field values. Multiple `orderby`
parameters are allowed, in which case resources are sorted by the first
attribute, then equal values are sorted by the second attribute, etc.

### Paging

A range of resources can be obtained by setting the `first` and `last`
(1-based) query parameters. The values default to the 1 and 20, respectively.

> NOTE: The Nuvla implementation will limit the number of returned resources
to 10000, independently of the values provided for these parameters. This is to
protect both the client and server from excessively large responses.

> NOTE: The selection of resources via these parameters is done after any
filtering and ordering.

### Subsetting

Using the `select` parameter allows you to select only certain attributes to
be returned by the server. Avoiding sending information that will not be useful
reduces the load on the network and the server.

Multiple attributes may be specified by providing a string with
comma-separated values or with multiple `select` parameters.

> NOTE: The server may return attributes that you have not selected in the
responses, such as the resource identifier. The server should always return the
selected attributes.

Example select values:

```c
name,description
```

### Filtering

The CIMI specification defines a simple, but powerful filtering language to
make sophisticated selections of resources. (See Section 4.1.6.1 of the full
[CIMI
specification](https://www.dmtf.org/sites/default/files/standards/documents/DSP0263_2.0.0.pdf).)
The syntax of the `filter` query parameter consists of infix binary comparisons
combined with `and` or `or` operators. Parentheses can be used to force the
ordering of operations. Whitespace is ignored.

Example filters:

```c
((alpha>2) and (beta>='2007-06-24T00:10:20Z') or (nested/value!=false)
```

```c
((tags='alpha') or (missing=null))
```

The following tables list the supported relational operators and types. All
comparisons use the natural ordering of the data type.

operator | description
-------- | -----------
= | equal
!= | not equal
< | less than
<= | less than or equal
> | greater than
>= | greater than or equal
^= | starts with (Nuvla extension)

type | comment
---- | -------
integer | integer values
string | single or double-quoted strings
date | date in ISO8601 format
boolean | either true or false
null | null values (Nuvla extension)

The Nuvla filtering implementation is a superset of the CIMI filtering
specification. These extensions include:

 - The arguments for binary operators can be specified in either order
   (attribute, value or value, attribute).

 - 'null' is supported as a literal value to allow the
   existence/non-existence of a value to be determined.

 - A 'prefix' or 'starts with' operator ('^=') is supported. This was
   judged to be generally useful and was easy to implement for
   Elasticsearch.

 - Nested attributes are supported with levels separated by slashes
   ('/'). This syntax is used elsewhere in the CIMI standard, but not
   specifically mentioned in the filter specification.

The [full
grammar](https://raw.githubusercontent.com/nuvla/api-server/master/code/resources/sixsq/nuvla/db/filter/cimi-filter-grammar.txt)
of our extended CIMI filtering is documented in GitHub.

### Aggregations

The Nuvla API also allows users to aggregate values over a set of filtered
documents. This is an extension to the CIMI specification. Aggregations are
useful for providing summary information concerning a set of documents, such as
the sum of an attribute, average, etc.

The general form of a query with an aggregation is:

`aggregation=algorithm:attributeName, ...`

Multiple aggregation expressions can be provided. The results of these
calculations are provided in the 'aggregations' section of the response. The
supported aggregations are described in the following table.

algorithm | description
--------- | -----------
min | minimum value
max | maximum value
sum | sum of values
avg | average of values
stats | statistics of values
extendedstats | extended statistics of values
value_count | number of values/documents
percentiles | binned percentiles of values
cardinality | cardinality of a field
missing | number of documents with missing field
terms | histogram of values and counts

## Deviations

Nuvla deviates from a strict CIMI implementation to simplify usage and to
provide a richer API.

### Media Types

The CIMI standard mandates the support of both XML and JSON. **The Nuvla
implementation only supports JSON** (and in a few cases URL-encoded forms).

### Authorization

The CIMI standard does not mandate any authentication and authorization
process. The schema of all resources in the Nuvla implementation includes an
'acl' key. **The Access Control List (ACL) of each resource describes who is
authorized to manage that resource.**

### Searches

The CIMI standard only provides for searches over resource collections with
the HTTP GET method. Because those filters can be quite long, there can be
issues with the length of the `GET` URL. Consequently, Nuvla clients may also
**search resource collections using the HTTP PUT method** with a body
containing the filters (and other parameters) as a URL-encoded form.

### Aggregations

As described earlier, Nuvla extends the CIMI standard to also
include aggregating values over a collection of resources.
"
  (:require
    [clojure.tools.logging :as log]
    [compojure.core :refer [ANY defroutes DELETE GET POST PUT]]
    [ring.util.response :as r]
    [sixsq.nuvla.auth.acl-resource :as a]
    [sixsq.nuvla.auth.utils :as auth]
    [sixsq.nuvla.db.impl :as db]
    [sixsq.nuvla.server.app.params :as p]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.dynamic-load :as dyn]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.resource-metadata :as md]
    [sixsq.nuvla.server.resources.spec.cloud-entry-point :as cep]
    [sixsq.nuvla.server.util.metadata :as gen-md]
    [sixsq.nuvla.server.util.response :as sr]))

;;
;; utilities
;;

(def ^:const resource-type (u/ns->type *ns*))


(def resource-acl {:owners   ["group/nuvla-admin"]
                   :view-acl ["group/nuvla-anon"]})


;; dynamically loads all available resources
(def resource-links
  (into {} (dyn/get-resource-links)))


;;
;; define validation function and add to standard multi-method
;;

(def validate-fn (u/create-spec-validation-fn ::cep/resource))


(defmethod crud/validate resource-type
  [resource]
  (validate-fn resource))


(defmethod crud/set-operations resource-type
  [resource request]
  (if (a/can-edit? resource request)
    (assoc resource :operations [(u/operation-map resource-type :edit)])
    (dissoc resource :operations)))


;;
;; CRUD operations
;;

(defn add
  "The cloud-entry-point resource is only created automatically at server startup
   if necessary.  It cannot be added through the API.  This function
   adds the minimal cloud-entry-point resource to the database."
  []
  (let [record (u/update-timestamps
                 {:acl           resource-acl
                  :id            resource-type
                  :resource-type resource-type})]
    (db/add resource-type record {:nuvla/authn auth/internal-identity})))


(defn retrieve-impl
  [{:keys [base-uri] :as request}]
  (r/response (-> (db/retrieve resource-type {})
                  (assoc :base-uri base-uri
                         :collections resource-links)
                  (crud/set-operations request))))


(defmethod crud/retrieve resource-type
  [request]
  (retrieve-impl request))


(defn edit-impl
  [{:keys [body] :as request}]
  (let [current (-> (db/retrieve resource-type {})
                    (assoc :acl resource-acl)
                    (a/throw-cannot-edit request))
        updated (-> body
                    (assoc :base-uri "http://example.org")
                    (u/strip-service-attrs))
        updated (-> (merge current updated)
                    (u/update-timestamps)
                    (assoc :collections resource-links)
                    (crud/set-operations request)
                    (crud/validate))]

    (db/edit updated request)))


(defmethod crud/edit resource-type
  [request]
  (edit-impl request))


;;
;; initialization: create cloud entry point if necessary
;;

(defn initialize
  []
  (std-crud/initialize resource-type ::cep/resource)
  (md/register (gen-md/generate-metadata ::ns ::cep/resource))

  (try
    (add)
    (log/info "Created" resource-type "resource")
    (catch Exception e
      (log/warn resource-type "resource not created; may already exist; message: " (str e)))))


;;
;; cloud-entry-point doesn't follow the usual service-context + '/resource-name/UUID'
;; pattern, so the routes must be defined explicitly.
;;

(defroutes routes
           (GET (str p/service-context resource-type) request
             (crud/retrieve (assoc-in request [:params :resource-name]
                                      resource-type)))
           (PUT (str p/service-context resource-type) request
             (crud/edit (assoc-in request [:params :resource-name]
                                  resource-type)))
           (ANY (str p/service-context resource-type) request
             (throw (sr/ex-bad-method request))))
