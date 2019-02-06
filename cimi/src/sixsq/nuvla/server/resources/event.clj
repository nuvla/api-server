(ns sixsq.nuvla.server.resources.event
  "
Event resources provide a timestamp for the occurrence of some action. These
are used within the SlipStream server to mark changes in the lifecycle of a
cloud application and for other important actions.

Some of the lifecycle changes that generate events are:

* State transition
* Termination (manual)
* First abort call (with reason)
* Scaling up or down

> WARNING: An event can **not** be updated, only deleted.

### List all Events

```shell
curl https://nuv.la/api/event/ -b cookie-user.txt -D -
```

The above command returns json structured like this:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 2284
Server: http-kit
Date: Thu, 19 Mar 2015 13:15:48 GMT

{
  \"events\" : [ {
    \"content\" : {
      \"resource\" : {
        \"href\" : \"Run/45614147-aed1-4a24-889d-6365b0b1f2cd\"
      },
      \"state\" : \"Started\"
    },
    \"updated\" : \"2015-03-19T09:39:38.238Z\",
    \"type\" : \"state\",
    \"created\" : \"2015-03-19T09:39:38.238Z\",
    \"id\" : \"Event/0d78db78-1b98-4bd1-ba14-bf378da68a66\",
    \"severity\" : \"medium\",
    \"acl\" : {
      \"owner\" : {
        \"type\" : \"USER\",
        \"principal\" : \"joe\"
      },
      \"rules\" : [ {
        \"type\" : \"ROLE\",
        \"principal\" : \"ANON\",
        \"right\" : \"ALL\"
      } ]
    },
    \"operations\" : [ {
      \"rel\" : \"http://sixsq.com/slipstream/1/Action/delete\",
      \"href\" : \"Event/0d78db78-1b98-4bd1-ba14-bf378da68a66\"
    } ],
    \"resourceURI\" : \"http://sixsq.com/slipstream/1/Event\",
    \"timestamp\" : \"2015-01-10T08:20:00.0Z\"
  } ],
  \"operations\" : [ {
    \"rel\" : \"http://sixsq.com/slipstream/1/Action/add\",
    \"href\" : \"Event\"
  } ],
  \"acl\" : {
    \"rules\" : [ {
      \"type\" : \"ROLE\",
      \"right\" : \"ALL\",
      \"principal\" : \"ANON\"
    } ],
    \"owner\" : {
      \"type\" : \"ROLE\",
      \"principal\" : \"ADMIN\"
    }
  },
  \"resourceURI\" : \"http://sixsq.com/slipstream/1/EventCollection\",
  \"id\" : \"Event\",
  \"count\" : 1
}
```

Lists all events accessible by the authenticated user. A user has the right to
see an event if she is the owner, or is a super user.

`GET https://nuv.la/api/event/`

### Create an Event

```shell
curl https://nuv.la/api/event -d \"{ \"acl\": {\"owner\": {\"type\": \"USER\", \"principal\": \"joe\"},    \"rules\": [{\"type\": \"ROLE\", \"prinipal\": \"ANON\", \"right\": \"ALL\"}]},    \"id\": \"123\",    \"created\" :  \"2015-01-16T08:20:00.0Z\",    \"updated\" : \"2015-01-16T08:20:00.0Z\",    \"resourceURI\" : \"http://slipstream.sixsq.com/ssclj/1/Event\",    \"timestamp\": \"2015-01-10T08:20:00.0Z\",    \"content\" :  { \"resource\":  {\"href\": \"Run/45614147-aed1-4a24-889d-6365b0b1f2cd\"},    \"state\" : \"Started\" } ,    \"type\": \"state\",    \"severity\": \"medium\"}\" -X POST -H \"Content-Type: application/json\" -b cookie-user.txt -D -
```

The above command returns a json structured like this:

```http
HTTP/1.1 201 Created
Location: Event/257cf1bd-1397-4296-8124-bb2213425b6e
Content-Type: application/json
Content-Length: 152
Server: http-kit
Date: Thu, 19 Mar 2015 12:47:38 GMT

{
  \"status\" : 201,
  \"message\" : \"created Event/257cf1bd-1397-4296-8124-bb2213425b6e\",
  \"resource-id\" : \"Event/257cf1bd-1397-4296-8124-bb2213425b6e\"
}
```

In case of error (e.g invalid value for `severity`), the following json is
returned (note that the message details the origin of the problem):

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json
Content-Length: 232
Server: http-kit
Date: Thu, 19 Mar 2015 13:48:11 GMT

{
  \"status\" : 400,
  \"message\" : \"resource does not satisfy defined schema: {:acl {:rules [{:prinicpal disallowed-key, :principal missing-required-key}]}, :severity (not (#{\"low\" \"high\" \"medium\" \"critical\"} \"urgent\"))}\"
}
```

Create an event that contains information (mainly timestamp and state) related
to a target resource.

#### HTTP Request

`POST https://nuv.la/api/event`

#### Body Parameters

Parameter   | Required  | Description
------------| --------  | -----------
timestamp   | true      | The timestamp (GMT time) of the event (not to be confused with created and updated timestamps of the resource representing the event)
content     | true      | Structure containing the resource reference (i.e the **target** of this event) and its state
type        | true      | Accepted values: `state` and `alarm`
severity    | true      | Accepted values: `critical`, `high`, `medium` and `low`

### Get an Event

```shell
curl https://nuv.la/api/event/4605ee06-ccda-48f5-a481-23c6ab296b0d -b cookie-user.txt -D -
```

> The above command returns a json structured like this:

```http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 869
Server: http-kit
Date: Thu, 19 Mar 2015 13:17:06 GMT

{
  \"content\" : {
    \"resource\" : {
      \"href\" : \"Run/45614147-aed1-4a24-889d-6365b0b1f2cd\"
    },
    \"state\" : \"Started\"
  },
  \"updated\" : \"2015-03-19T12:46:16.766Z\",
  \"type\" : \"state\",
  \"created\" : \"2015-03-19T12:46:16.766Z\",
  \"id\" : \"Event/4605ee06-ccda-48f5-a481-23c6ab296b0d\",
  \"severity\" : \"medium\",
  \"acl\" : {
    \"owner\" : {
      \"type\" : \"USER\",
      \"principal\" : \"joe\"
    },
    \"rules\" : [ {
      \"type\" : \"ROLE\",
      \"principal\" : \"ANON\",
      \"right\" : \"ALL\"
    } ]
  },
  \"operations\" : [ {
    \"rel\" : \"http://sixsq.com/slipstream/1/Action/delete\",
    \"href\" : \"Event/4605ee06-ccda-48f5-a481-23c6ab296b0d\"
  } ],
  \"resourceURI\" : \"http://sixsq.com/slipstream/1/Event\",
  \"timestamp\" : \"2015-01-10T08:20:00.0Z\"
}
```

Get a specific event.

#### HTTP Request

`GET https://nuv.la/api/event/<event-uuid>`


### Delete an Event

```shell
curl -X DELETE  https://nuv.la/api/event/85d787ea-a06e-4577-bf2b-1e681d5769a2 -b cookie-user.txt -D -
```

> The above command returns a json structured like this:

```http
HTTP/1.1 204 No Content
Content-Type: application/json
Content-Length: 152
Server: http-kit
Date: Thu, 19 Mar 2015 13:26:27 GMT
```

> When providing an invalid event-uuid, here is the response:

```http
HTTP/1.1 404 Not Found
Content-Type: application/json
Content-Length: 102
Server: http-kit
Date: Thu, 19 Mar 2015 13:35:57 GMT

{
  \"status\" : 404,
  \"message\" : \"Event/wrong-uuid not found\",
  \"resource-id\" : \"Event/wrong-uuid\"
}
```

Delete a specific (the event-uuid is known) event.

#### HTTP Request

`DELETE https://nuv.la/api/event/<event-uuid>`
  "
  (:require
    [sixsq.nuvla.auth.acl :as a]
    [sixsq.nuvla.server.resources.common.crud :as crud]
    [sixsq.nuvla.server.resources.common.schema :as c]
    [sixsq.nuvla.server.resources.common.std-crud :as std-crud]
    [sixsq.nuvla.server.resources.common.utils :as u]
    [sixsq.nuvla.server.resources.event.utils :as event-utils]
    [sixsq.nuvla.server.resources.spec.event :as event]))

(def ^:const resource-type (u/ns->type *ns*))

(def ^:const resource-tag :events)
(def ^:const resource-name event-utils/resource-name)
(def ^:const resource-url event-utils/resource-url)
(def ^:const collection-name "EventCollection")

(def ^:const resource-uri event-utils/resource-uri)
(def ^:const collection-uri (str c/cimi-schema-uri collection-name))

(def collection-acl event-utils/collection-acl)

;;
;; "Implementations" of multimethod declared in crud namespace
;;

(def validate-fn (u/create-spec-validation-fn ::event/event))
(defmethod crud/validate
  resource-uri
  [resource]
  (validate-fn resource))


(defmethod crud/add resource-name
  [request]
  (event-utils/add-impl request))


(def retrieve-impl (std-crud/retrieve-fn resource-name))

(defmethod crud/retrieve resource-name
  [request]
  (retrieve-impl request))

(def delete-impl (std-crud/delete-fn resource-name))

(defmethod crud/delete resource-name
  [request]
  (delete-impl request))

;;
;; available operations
;;
(defmethod crud/set-operations resource-uri
  [resource request]
  (try
    (a/can-modify? resource request)
    (let [href (:id resource)
          ^String resourceURI (:resourceURI resource)
          ops (if (.endsWith resourceURI "Collection")
                [{:rel (:add c/action-uri) :href href}]
                [{:rel (:delete c/action-uri) :href href}])]
      (assoc resource :operations ops))
    (catch Exception e
      (dissoc resource :operations))))

;;
;; collection
;;
(def query-impl (std-crud/query-fn resource-name collection-acl collection-uri resource-tag))
(defmethod crud/query resource-name
  [{{:keys [orderby]} :cimi-params :as request}]
  (query-impl (assoc-in request [:cimi-params :orderby] (if (seq orderby) orderby [["timestamp" :desc]]))))


;;
;; initialization
;;
(defn initialize
  []
  (std-crud/initialize resource-url ::event/event))
