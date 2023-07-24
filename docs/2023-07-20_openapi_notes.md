# Some notes on the Nuvla API -> OpenAPI Mapping

## What is OpenAPI

> The OpenAPI Specification (OAS) provides a consistent means to carry information through each stage of the API lifecycle.
> It is a specification language for HTTP APIs that defines structure and syntax in a way that is not wedded to the programming language the API is created in.
> API specifications are typically written in YAML or JSON, allowing for easy sharing and consumption of the specification.



Most relevant spec sections:

- info
- paths
    - pathItems
        - /{path}
            - get/post/put/patch/delete
                - tags
                - description
                - operationId
                - parameters
                - requestBody
                - responses
                - deprecated
                - security
- tags
- components
    - schemas: OpenAPI supports schemas to define what type of inputs and outputs each operation supports.
      OpenAPI schemas are an extension of JSON schemas.
    - responses: in particular, for each response code, the following information can be returned:
        - description
        - headers
        - content
        - links
    - parameters
    - requestBodies
    - headers
- security

For an example see https://petstore.swagger.io/


### Target audience

The target audience is expected to be final users of the Nuvla platform.
As such, we do not need to expose info and endpoints that are only needed by Nuvla server administrators.
For example, we do not want to expose operations to add new session templates, which is an operation reserved to Nuvla server administrators.


### Implementation options

2 possible approaches are considered: manual and semi-generated.

#### Manual approach (API spec first)

Resource operations are manually modeled one by one in OpenAPI

pros:
- can be tailored to exactly what we want in each case
- easier to implement
- easier to implement the api in multiple languages
- simpler to integrate the role of a technical writer

cons:
- can potentially diverge from the actual implementation. In particular, there is not an actual validation
  of requests and responses against their schemas (which is only defined in OpenAPI and not in Clojure spec)
  Unless we generate the Clojure server stubs from the spec, but:
    - there is no Clojure server generator (write one ?)
    - we would have to change the routing of the current application and re-test all the endpoints carefully:
        - I think its more risky than generating the OpenAPI spec from Clojure spec.
- (might require more effort on devs to keep OpenAPI aligned when adding/modifying resource types).


#### Semi-generated approach

- Resource operations are generated via resource metadata and reflection
- Schemas generated from Clojure spec
- Some parts still manual (proper descriptions, examples, manual override of the resource/schema specs for special cases)

pros:
- better alignment between server code and the OpenAPI spec
- (possibly better experience for devs adding/modifying resources)

cons:
- more difficult to implement
- might be more difficult to customize for special cases


### What to expose?

In Nuvla all user-facing objects are represented as resources.
We need to decide which Nuvla resources and operations we do want to document publicly.
For sure we need to expose the resources and operations needed to create a new session:

- PUT /session : queries for existing sessions
- POST /session : creates a new session
- DELETE /session : deletes a session

- GET /session_template : queries for supported session types
  (no need to expose other operations on the session_template resource)

For the rest of the resources, it should be decided case by case.

#### Semi-generated approach

In the semi-generated approach it is probably a good idea to use a whitelist approach, and
expose resources/operations one by one on a as-needed basis.
It is better to forget to expose some resources or operations and add them later than to expose
everything and incur the risk of confusing our users.


### Tags

OpenAPI operations can be grouped together by adding tags.
A natural way of tagging operations for the Nuvla API is to have tag for each exposed resource types and to tag
all operations on a given resource type in the same way. This will allow OpenAPI UIs to present the operations
in a coherent way, and to show a summary for the group.
As such, the description provided for tags will be pretty important for the final user experience.

#### Where to take the tag description from

The api-server code already contains a pretty extensive description of each resource type in the namespace docstring
for the specific resource type. Even though that description usually contains information that can be useful for a
final user of the Nuvla platform, it can also contain details that might not be relevant to the final user.

As such, we probably want to have the possibility to use the namespace docstring when it makes sense, but we also
want to have the possibility to override the description for OpenAPI when it goes too much into implementation details
that are not relevant for the final user.

### Operations on resources

The Nuvla API exposes 2 types of operations on resources:
- SCRUD operations: Search, Create, Read, Update, Delete
- Actions: custom actions that do not fall under the SCRUD umbrella

Which SCRUD operations and actions to expose via OpenAPI should be decided on a resource type basis.

#### Semi-generated approach

There is the question of whether a whitelist or a blacklist approach is better.
In the whitelist approach the developer must list explicitly which operations to expose:
- pros: there must be intentionality, no risk of exposing operations unwillingly
- cons: more things to remember for the developer

In the blacklist approach the implemented operations are exposed automatically:
- pros: no risk of forgetting to expose the operations to OpenAPI
- cons: expose too much, some operations might only make sense for a Nuvla server admin for example

> Personal preference: whitelist approach, better to forget some operations and add them later than to expose too much and
confuse the user.

#### What can be exposed about resource operations

- operationId: (will be used internally by the spec for link references and by client generators to name functions and methods)
- description: will be shown by Swagger UI or in the generated documentation
- externalDocs: it is possible to also link to external documents for additional info
    - I cannot think of a use case, but maybe it could turn out to be useful for some cases
- parameters: the input parameters to the operation. They can be either `path`, `query`, `header` or `cookie` parameters.
    - `header` or `cookie` parameters are used in Nuvla to pass in the session token
    - `path` parameters are used to pass the resource id in operations on specific resources
    - `query` parameters are used in query operations to pass in CIMI filters
- requestBody: the body of the request
- responses: the list of possible responses as they are returned from executing this operation.
- deprecated: Declares this operation to be deprecated. Consumers SHOULD refrain from usage of the declared operation. Default value is `false`.
- security: A declaration of which security mechanisms can be used for this operation. The list of values includes alternative security requirement objects that can be used. Only one of the security requirement objects need to be satisfied to authorize a request. To make security optional, an empty security requirement (`{}`) can be included in the array. This definition overrides any declared top-level security. To remove a top-level security declaration, an empty array can be used.


### Request and response schemas

We can identify at least 3 types of schemas that are needed to represent Nuvla resources/operations correctly in OpenAPI:
1. resource schemas returned by retrieve operations (GET / PUT)
2. resource schemas accepted by create operations (POST)
3. resource schemas accepted by patch operations (PATCH)

The api-server codebase defines the spec of resources as they are stored in Elastic Search, which often is not exactly the same
as what is sent as request from the clients or sent back as response to the clients.

Therefore, we would need to provide request and response specs for the resources that we want to expose via OpenAPI.

One option suggested by Mario to facilitate this task is to use a tool to generate the specs from examples.
There is a library to do that for Clojure Spec: https://github.com/stathissideris/spec-provider.
We could integrate it in middlewares and have the specs be generated from real data, or at least an initial version to be refined manually.



#### Semi-generated approach - Problems encountered

In my experiments I used the `spec-tools` library to go from Clojure spec to OpenAPI schemas.
It works, but it has some criticalities:
- passing the spec of a resource produces a single nested spec: common fields will be repeated for each resource
- some of the fields cause a compilation error when trying to generate a client (at least in Java)
  We need more control on the output than what `spec-tools` json schema transformer is providing.
  We could write a fork that:
- generates one json schema per Clojure spec
- checks a special new annotations (i.e ::openapi/schema-name and ::openapi/field-name) in Clojure specs to use custom
  schema and field names in order to avoid conflicts in generated clients.

#### Templated resource schemas

A templated resource is a resource that references a template, and its schema varies depending on the chosen template.
The OpenAPI spec seems to support exactly this use case via the
https://swagger.io/specification/#discriminator-object
and the `oneOf` keyword.
For templated resources the discriminator should be the `ref` field.


### CIMI Query parameters

CIMI query parameters can be represented as parameters of type `query` in OpenAPI.
Some example of usage can be put in the `examples` section.

## Nuvla API -> OpenAPI mapping

Most relevant sections from the [OpenAPI spec](https://swagger.io/specification/),
annotated with the specifics of the mapping to the Nuvla API.

> Try to keep updated with code changes.

### OpenAPI object

| Field Name | Description                                                                                                                                                                                                                                                                                                                                                                                                 | Nuvla OpenAPI Spec                                                                                | Notes                                                                                                                                          |
| --- |-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| openapi | REQUIRED. This string MUST be the [version number](https://swagger.io/specification/#versions) of the OpenAPI Specification that the OpenAPI document uses. The `openapi` field SHOULD be used by tooling to interpret the OpenAPI document. This is *not* related to the API [`info.version`](https://swagger.io/specification/#info-version) string.                                                      | 3.1.0 (see notes)                                                                                 | use 3.1.0 unless we see friction with the tooling, use 3.0.3 otherwise. |
| info | REQUIRED. Provides metadata about the API. The metadata MAY be used by tooling as required.                                                                                                                                                                                                                                                                                                                 | See below                                                                                         |                                                                                                                                                |
| jsonSchemaDialect | The default value for the `$schema` keyword within [Schema Objects](https://swagger.io/specification/#schema-object) contained within this OAS document. This MUST be in the form of a URI.                                                                                                                                                                                                                 |                                                                                                   |                                                                                                                                                |
| servers | An array of Server Objects, which provide connectivity information to a target server. If the `servers` property is not provided, or is an empty array, the default value would be a [Server Object](https://swagger.io/specification/#server-object) with a [url](https://swagger.io/specification/#server-url) value of `/`.                                                                              | `{"url":"https://nuvla.io/api", "description":"Main (production) server"}`                        | Do we want to also expose a dev environment for external developers ?                                                                          |
| paths | The available paths and operations for the API.                                                                                                                                                                                                                                                                                                                                                             | See below                                                                                         |                                                                                                                                                |
| webhooks | Webhooks                                                                                                                                                                                                                                                                                                                                                                                                    |                                                                                                   | Not analysed yet                                                                                                                               | The incoming webhooks that MAY be received as part of this API and that the API consumer MAY choose to implement. Closely related to the `callbacks` feature, this section describes requests initiated other than by an API call, for example by an out of band registration. The key name is a unique string to refer to each webhook, while the (optionally referenced) Path Item Object describes a request that may be initiated by the API provider and the expected responses. An [example](https://github.com/-o-a-i/-open-a-p-i--specification/blob/main/examples/v3.1/webhook-example.yaml) is available. |
| components | An element to hold various types of reusable objects.                                                                                                                                                                                                                                                                                                                                                       | See below                                                                                         |                                                                                                                                                |
| security | A declaration of which security mechanisms can be used across the API. The list of values includes alternative security requirement objects that can be used. Only one of the security requirement objects need to be satisfied to authorize a request. Individual operations can override this definition. To make security optional, an empty security requirement (`{}`) can be included in the array.   | See below                                                                                       |                                                                                                                                                |
| tags | A list of tags used by the document with additional metadata. The order of the tags can be used to reflect on their order by the parsing tools. Not all tags that are used by the [Operation Object](https://swagger.io/specification/#operation-object) must be declared. The tags that are not declared MAY be organized randomly or based on the tools' logic. Each tag name in the list MUST be unique. | See below                                                                                         |                                                                                                                                                |
| externalDocs | Additional external documentation.                                                                                                                                                                                                                                                                                                                                                                          | `{"description":"Find out more about Nuvla", "url":"https:\/\/docs.nuvla.io\/nuvla\/user-guide"}` |                                                                                                                                                |

### Info object

| Field Name | Description | Nuvla OpenAPI Spec                                                                                                             | Notes                                                                                                                                                                                                                                                                                                                                                                           |
| --- | --- |--------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| title | REQUIRED. The title of the API. | Nuvla API                                                                                                                      |                                                                                                                                                                                                                                                                                                                                                                                 |
| summary | A short summary of the API. | HTTP-based RESTful API, for the management of Nuvla resources.                                                                 |                                                                                                                                                                                                                                                                                                                                                                                 |
| description | A description of the API. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation. |  | Long description here, probably explain the typical flow, how to authenticate and how to pass the session in subsequent calls.                                                                                                                                                                                                                                                                             |
| termsOfService | A URL to the Terms of Service for the API. This MUST be in the form of a URL. | https://docs.nuvla.io/legal/legal/                                                                                             | better ideas?                                                                                                                                                                                                                                                                                                                                                                   |
| contact | The contact information for the exposed API. | `{"name":"API Support", "email":"support@sixsq.com"}`                                                                           |                                                                                                                                                                                                                                                                                                                                            |
| license | The license information for the exposed API. | `{"name":"Apache 2.0", "url":"https:\/\/www.apache.org\/licenses\/LICENSE-2.0.html"}`                                           |                                                                                                                                                                                                                                                                                                                                                                                 |
| version | REQUIRED. The version of the OpenAPI document (which is distinct from the [OpenAPI Specification version](https://swagger.io/specification/#oas-version) or the API implementation version). |                                                                                                                                | From Khaled: "Some ways to get the api version: Maybe from the api server jar manifest. Maybe we can generate a file with version into resources folder.  But it's not an easy question to decide which version to show. Because there is the case where the api-server resources are extended with a private part from another clojure project that add some extra resources." |



### Tags

| Field Name | Description | Nuvla OpenAPI Spec | Notes |
| --- | --- |--------------------|-------|
| name | REQUIRED. The name of the tag. |                    |       |
| description | A description for the tag. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation. |                    |       |
| externalDocs | Additional external documentation for this tag. |                    |       |


Tags assigned to paths end up being the grouping used by the Swagger UI.
So probably it is a good idea to have at least one tag per exposed Nuvla resource type.


### Paths

> Holds the relative paths to the individual endpoints and their operations. The path is appended to the URL from the Server Object in order to construct the full URL.

It must be a map from path name to a path item. A path item is a map with the following fields:

| Field Name | Description   | Nuvla OpenAPI Spec |  Notes   |
| --- |---------------|--------------------|-----|
| $ref | Allows for a referenced definition of this path item. The referenced structure MUST be in the form of a [Path Item Object](https://swagger.io/specification/#path-item-object). In case a Path Item Object field appears both in the defined object and the referenced object, the behavior is undefined. See the rules for resolving [Relative References](https://swagger.io/specification/#relative-references-u-r-i). |                    |     |
| summary | An optional, string summary, intended to apply to all operations in this path. |                    |     |
| description | An optional, string description, intended to apply to all operations in this path. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation. |                    |     |
| get | A definition of a GET operation on this path. |                    |     |
| put | A definition of a PUT operation on this path. |                    |     |
| post | A definition of a POST operation on this path. |                    |     |
| delete | A definition of a DELETE operation on this path. |                    |     |
| options | A definition of a OPTIONS operation on this path. |                    |     |
| head | A definition of a HEAD operation on this path. |                    |     |
| patch | A definition of a PATCH operation on this path. |                    |     |
| trace | A definition of a TRACE operation on this path. |                    |     |
| servers | An alternative `server` array to service all operations in this path. |                    |     |
| parameters | |                    |     | A list of parameters that are applicable for all the operations described under this path. These parameters can be overridden at the operation level, but cannot be removed there. The list MUST NOT include duplicated parameters. A unique parameter is defined by a combination of a [name](https://swagger.io/specification/#parameter-name) and [location](https://swagger.io/specification/#parameter-in). The list can use the [Reference Object](https://swagger.io/specification/#reference-object) to link to parameters that are defined at the [OpenAPI Object's components/parameters](https://swagger.io/specification/#components-parameters). |



### Paths operations

> Describes a single API operation on a path.

| Field Name | Description | Nuvla OpenAPI Spec    |  Notes   |
| --- | --- |-----|-----|
| tags | A list of tags for API documentation control. Tags can be used for logical grouping of operations by resources or any other qualifier. |     |     |
| summary | A short summary of what the operation does. |     |     |
| description | A verbose explanation of the operation behavior. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation. |     |     |
| externalDocs | Additional external documentation for this operation. |     |     |
| operationId | Unique string used to identify the operation. The id MUST be unique among all operations described in the API. The operationId value is case-sensitive. Tools and libraries MAY use the operationId to uniquely identify an operation, therefore, it is RECOMMENDED to follow common programming naming conventions. |     |     |
| parameters | [Reference Object](https://swagger.io/specification/#reference-object) |     |     | A list of parameters that are applicable for this operation. If a parameter is already defined at the [Path Item](https://swagger.io/specification/#path-item-parameters), the new definition will override it but can never remove it. The list MUST NOT include duplicated parameters. A unique parameter is defined by a combination of a [name](https://swagger.io/specification/#parameter-name) and [location](https://swagger.io/specification/#parameter-in). The list can use the [Reference Object](https://swagger.io/specification/#reference-object) to link to parameters that are defined at the [OpenAPI Object's components/parameters](https://swagger.io/specification/#components-parameters). |
| requestBody | [Reference Object](https://swagger.io/specification/#reference-object) |     |     | The request body applicable for this operation. The `requestBody` is fully supported in HTTP methods where the HTTP 1.1 specification [RFC7231](https://tools.ietf.org/html/rfc7231#section-4.3.1) has explicitly defined semantics for request bodies. In other cases where the HTTP spec is vague (such as [GET](https://tools.ietf.org/html/rfc7231#section-4.3.1), [HEAD](https://tools.ietf.org/html/rfc7231#section-4.3.2) and [DELETE](https://tools.ietf.org/html/rfc7231#section-4.3.5)), `requestBody` is permitted but does not have well-defined semantics and SHOULD be avoided if possible. |
| responses | The list of possible responses as they are returned from executing this operation. |     |     |
| callbacks | [Reference Object](https://swagger.io/specification/#reference-object) |     |     | A map of possible out-of band callbacks related to the parent operation. The key is a unique identifier for the Callback Object. Each value in the map is a [Callback Object](https://swagger.io/specification/#callback-object) that describes a request that may be initiated by the API provider and the expected responses. |
| deprecated | Declares this operation to be deprecated. Consumers SHOULD refrain from usage of the declared operation. Default value is `false`. |     |     |
| security | A declaration of which security mechanisms can be used for this operation. The list of values includes alternative security requirement objects that can be used. Only one of the security requirement objects need to be satisfied to authorize a request. To make security optional, an empty security requirement (`{}`) can be included in the array. This definition overrides any declared top-level [`security`](https://swagger.io/specification/#oas-security). To remove a top-level security declaration, an empty array can be used. |     |     |
| servers | An alternative `server` array to service this operation. If an alternative `server` object is specified at the Path Item Object or Root level, it will be overridden by this value. |     |     |


> NOTE: Path operations can be linked.

### Parameters

> Describes a single operation parameter.
>
> A unique parameter is defined by a combination of a [name](https://swagger.io/specification/#parameter-name) and [location](https://swagger.io/specification/#parameter-in).
>
> ##### Parameter Locations
>
> There are four possible parameter locations specified by the `in` field:
>
> -   path - Used together with [Path Templating](https://swagger.io/specification/#path-templating), where the parameter value is actually part of the operation's URL. This does not include the host or base path of the API. For example, in `/items/{itemId}`, the path parameter is `itemId`.
> -   query - Parameters that are appended to the URL. For example, in `/items?id=###`, the query parameter is `id`.
> -   header - Custom headers that are expected as part of the request. Note that [RFC7230](https://tools.ietf.org/html/rfc7230#page-22) states header names are case insensitive.
> -   cookie - Used to pass a specific cookie value to the API.

| Field Name                                           | Description                                                                                                                                                                                                                                                                                                                                                                                                                                      |    Nuvla OpenAPI Spec | Notes |
|------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----|-------|
| name            | REQUIRED. The name of the parameter. Parameter names are *case sensitive*.                                                                                                                                                                                                                                                                                                                                                                       |     |       |
| in              | REQUIRED. The location of the parameter. Possible values are `"query"`, `"header"`, `"path"` or `"cookie"`.                                                                                                                                                                                                                                                                                                                                      |     |       |
| description     | A brief description of the parameter. This could contain examples of use. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation.                                                                                                                                                                                                                                                                            |     |       |
| required        | Determines whether this parameter is mandatory. If the [parameter location](https://swagger.io/specification/#parameter-in) is `"path"`, this property is REQUIRED and its value MUST be `true`. Otherwise, the property MAY be included and its default value is `false`.                                                                                                                                                                       |     |       |
| deprecated      | Specifies that a parameter is deprecated and SHOULD be transitioned out of usage. Default value is `false`.                                                                                                                                                                                                                                                                                                                                      |     |       |
| allowEmptyValue | Sets the ability to pass empty-valued parameters. This is valid only for `query` parameters and allows sending a parameter with an empty value. Default value is `false`. If [`style`](https://swagger.io/specification/#parameter-style) is used, and if behavior is `n/a` (cannot be serialized), the value of `allowEmptyValue` SHALL be ignored. Use of this property is NOT RECOMMENDED, as it is likely to be removed in a later revision. |     |       |

The rules for serialization of the parameter are specified in one of two ways. For simpler scenarios, a [`schema`](https://swagger.io/specification/#parameter-schema) and [`style`](https://swagger.io/specification/#parameter-style) can describe the structure and syntax of the parameter.

| Field Name | Description | Nuvla OpenAPI Spec | Notes |
| --- | --- |--------------------|-------|
| style | Describes how the parameter value will be serialized depending on the type of the parameter value. Default values (based on value of `in`): for `query` - `form`; for `path` - `simple`; for `header` - `simple`; for `cookie` - `form`. |                    |       |
| explode | When this is true, parameter values of type `array` or `object` generate separate parameters for each value of the array or key-value pair of the map. For other types of parameters this property has no effect. When [`style`](https://swagger.io/specification/#parameter-style) is `form`, the default value is `true`. For all other styles, the default value is `false`. |                    |       |
| allowReserved | Determines whether the parameter value SHOULD allow reserved characters, as defined by [RFC3986](https://tools.ietf.org/html/rfc3986#section-2.2) `:/?#[]@!$&'()*+,;=` to be included without percent-encoding. This property only applies to parameters with an `in` value of `query`. The default value is `false`. |                    |       |
| schema | The schema defining the type used for the parameter. |                    |       |
| example | Example of the parameter's potential value. The example SHOULD match the specified schema and encoding properties if present. The `example` field is mutually exclusive of the `examples` field. Furthermore, if referencing a `schema` that contains an example, the `example` value SHALL *override* the example provided by the schema. To represent examples of media types that cannot naturally be represented in JSON or YAML, a string value can contain the example with escaping where necessary. |                    |       |
| examples | [Reference Object](https://swagger.io/specification/#reference-object)] |                    |       | Examples of the parameter's potential value. Each example SHOULD contain a value in the correct format as specified in the parameter encoding. The `examples` field is mutually exclusive of the `example` field. Furthermore, if referencing a `schema` that contains an example, the `examples` value SHALL *override* the example provided by the schema. |

For more complex scenarios, the [`content`](https://swagger.io/specification/#parameter-content) property can define the media type and schema of the parameter. A parameter MUST contain either a `schema` property, or a `content` property, but not both. When `example` or `examples` are provided in conjunction with the `schema` object, the example MUST follow the prescribed serialization strategy for the parameter.

| Field Name | Description | Nuvla OpenAPI Spec | Notes |
| --- | --- |--------------------|-------|
| content | A map containing the representations for the parameter. The key is the media type and the value describes it. The map MUST only contain one entry. |                    |       |

### Request body

| Field Name | Description |  Nuvla OpenAPI Spec   | Notes |
| --- | --- |-----|-------|
| description | A brief description of the request body. This could contain examples of use. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation. |     |       |
| content | REQUIRED. The content of the request body. The key is a media type or [media type range](https://tools.ietf.org/html/rfc7231#appendix--d) and the value describes it. For requests that match multiple keys, only the most specific key is applicable. e.g. text/plain overrides text/* |     |       |
| required | Determines if the request body is required in the request. Defaults to `false`. |     |       |


### Responses

For each response status code, a response object can be specified:

| Field Name | Description | Nuvla OpenAPI Spec   | Notes |
| --- | --- |-----|-------|
| description | REQUIRED. A description of the response. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation. |     |       |
| headers | [Reference Object](https://swagger.io/specification/#reference-object) |     |       | Maps a header name to its definition. [RFC7230](https://tools.ietf.org/html/rfc7230#page-22) states header names are case insensitive. If a response header is defined with the name `"Content-Type"`, it SHALL be ignored. |
| content | A map containing descriptions of potential response payloads. The key is a media type or [media type range](https://tools.ietf.org/html/rfc7231#appendix--d) and the value describes it. For responses that match multiple keys, only the most specific key is applicable. e.g. text/plain overrides text/* |     |       |
| links | [Reference Object](https://swagger.io/specification/#reference-object) |     |       | A map of operations links that can be followed from the response. The key of the map is a short name for the link, following the naming constraints of the names for [Component Objects](https://swagger.io/specification/#components-object). |

### Callbacks

Callback Object
A map of possible out-of band callbacks related to the parent operation. Each value in the map is a Path Item Object that describes a set of requests that may be initiated by the API provider and the expected responses. The key value used to identify the path item object is an expression, evaluated at runtime, that identifies a URL to use for the callback operation.

To describe incoming requests from the API provider independent from another API call, use the webhooks field.

> Question: do we have this use case in the Nuvla API ?

### Media type objects

Used to represent parameters and request/response body contents.

| Field Name | Description | Nuvla OpenAPI Spec | Notes |
| --- | --- |--------------------|-------|
| schema | The schema defining the content of the request, response, or parameter. |                    |       |
| example | Example of the media type. The example object SHOULD be in the correct format as specified by the media type. The `example` field is mutually exclusive of the `examples` field. Furthermore, if referencing a `schema` which contains an example, the `example` value SHALL *override* the example provided by the schema. |                    |       |
| examples | Examples of the media type. Each example object SHOULD match the media type and specified schema if present. The `examples` field is mutually exclusive of the `example` field. Furthermore, if referencing a `schema` which contains an example, the `examples` value SHALL *override* the example provided by the schema. | |                    |      
| encoding | A map between a property name and its encoding information. The key, being the property name, MUST exist in the schema as a property. The encoding object SHALL only apply to `requestBody` objects when the media type is `multipart` or `application/x-www-form-urlencoded`. |                    |       |


### Components

> Holds a set of reusable objects for different aspects of the OAS. All objects defined within the components object will have no effect on the API unless they are explicitly referenced from properties outside the components object.

| Field Name | Description                                                                                         | Nuvla OpenAPI Spec | Notes |
| --- |-----------------------------------------------------------------------------------------------------|--------------------|-------|
| schemas | An object to hold reusable [Schema Objects](https://swagger.io/specification/#schema-object).       |                    |       |
| responses | An object to hold reusable [Response Objects](https://swagger.io/specification/#response-object).   |                    |       |
| parameters | An object to hold reusable [Parameter Objects](https://swagger.io/specification/#parameter-object). |                    |       |
| examples | An object to hold reusable [Example Objects](https://swagger.io/specification/#example-object). |                    |       |
| requestBodies | An object to hold reusable [Request Body Objects](https://swagger.io/specification/#request-body-object). |                    |       |
| headers | An object to hold reusable [Header Objects](https://swagger.io/specification/#header-object). |                    |       |
| securitySchemes | An object to hold reusable [Security Scheme Objects](https://swagger.io/specification/#security-scheme-object). |                    |       |
| links | An object to hold reusable [Link Objects](https://swagger.io/specification/#link-object). |                    |       |
| callbacks | An object to hold reusable [Callback Objects](https://swagger.io/specification/#callback-object). |                    |       |
| pathItems | An object to hold reusable [Path Item Object](https://swagger.io/specification/#path-item-object). |                    |       |


### Security Scheme Object

> Defines a security scheme that can be used by the operations.
>
> Supported schemes are HTTP authentication, an API key (either as a header, a cookie parameter or as a query parameter),
> mutual TLS (use of a client certificate), OAuth2's common flows (implicit, password, client credentials and authorization code) as defined in RFC6749,
> and OpenID Connect Discovery. Recommended for most use case is Authorization Code Grant flow with PKCE.

| Field Name | Applies To          | Description                                                                                                                                                                                                                                                                                                                       | Nuvla OpenAPI Spec   | Notes |
| --- |---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----|-------|
| type | Any                 | REQUIRED. The type of the security scheme. Valid values are `"apiKey"`, `"http"`, `"mutualTLS"`, `"oauth2"`, `"openIdConnect"`.                                                                                                                                                                                                   |     |       |
| description | Any                 | A description for security scheme. [CommonMark syntax](https://spec.commonmark.org/) MAY be used for rich text representation.                                                                                                                                                                                                    |     |       |
| name | `apiKey`            | REQUIRED. The name of the header, query or cookie parameter to be used.                                                                                                                                                                                                                                                           |     |       |
| in | `apiKey`            | REQUIRED. The location of the API key. Valid values are `"query"`, `"header"` or `"cookie"`.                                                                                                                                                                                                                                      |     |       |
| scheme | `http`              | REQUIRED. The name of the HTTP Authorization scheme to be used in the [Authorization header as defined in RFC7235](https://tools.ietf.org/html/rfc7235#section-5.1). The values used SHOULD be registered in the [IANA Authentication Scheme registry](https://www.iana.org/assignments/http-authschemes/http-authschemes.xhtml). |     |       |
| bearerFormat | `http` (`"bearer"`) | A hint to the client to identify how the bearer token is formatted. Bearer tokens are usually generated by an authorization server, so this information is primarily for documentation purposes.                                                                                                                                  |     |       |
| flows | `oauth2`            | REQUIRED. An object containing configuration information for the flow types supported.                                                                                                                                                                                                                                            |     |       |
| openIdConnectUrl | `openIdConnect`     | REQUIRED. OpenId Connect URL to discover OAuth2 configuration values. This MUST be in the form of a URL. The OpenID Connect standard requires the use of TLS.                                                                                                                                                                     |     |       |


## Tentative implementation plan / estimated effort

### Manual approach

[2/3WD per resource type, initially] to:
- write the OpenAPI spec for all supported operations
- write the json schemas for the types involved in the operations above
- write user friendly descriptions
- write meaningful examples (which will be shown in Swagger UI or in the generated docs)
- do some testing via rest and via generated client (Java or others) and make needed adjustments

There are 134 registered resource types in resource-metadata. Many probably don't need to be exposed, and the estimates
above will probably go down after a few resource types are implemented and all the patterns are clear.

### Semi-generated approach

Missing pieces to make it work similarly to the hand-made example of the manual approach :

- [3WD] do the manual exercise for a couple more resource types at least
- [2WD] adaptation of `spec-tools` json schema transformer to produce flattened schemas
- [2WD] make sure `oneOf` and `allOf` schemas are generated correctly from Clojure s/or and s/merge
- [1WD] implementation of blacklisting or whitelisting for resource types and operations
- [?]   manual or generated request spec for templated resources ?
- [1WD] operation ids generation and possibility to manually override
- [2WD] add support for ::openapi/xyz annotations in Clojure specs
    - ::openapi/schema-name To overcome issues in generated clients
    - ::openapi/field-name  To overcome issues in generated clients
    - ::openapi/public-op?  To expose operations publicly (e.g `/session` endpoints)
- [2/3WD per resource type, initially] to:
    - write/generate the clojure specs for requests and responses and make sure all tests still pass
    - write user friendly descriptions
    - write meaningful examples (which will be shown in Swagger UI or in the generated docs).
    - do some testing via rest and via generated client (Java or others) and make needed adjustments
    
    There are 134 registered resource types in resource-metadata. Many probably don't need to be exposed, and the estimates
    above will probably go down after a few resource types are implemented and all the patterns are clear.

Additional developments to take advantage of the request/response specs :
- [3WD] add request/response validation

