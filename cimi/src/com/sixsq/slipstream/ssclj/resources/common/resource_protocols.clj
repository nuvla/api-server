(ns com.sixsq.slipstream.ssclj.resources.common.resource-protocols)

;;
;; Currently the dynamic loading of a resource depends on the existence (or
;; absence) of particular vars.
;;
;; When grouping the functionality into one or more protocols, there will
;; still need to be a "magic" function or var within the resource's namespace
;; that acts as a hook to pull the resource into the web application.
;;
;; Two alternatives are:
;;
;;   * A no-arg function that reifies an instance of the resource or
;;   * A var (probably using delay) that contains a singleton instance.
;;
;; The name of the function/var would have to be defined by convention.
;;

;;
;; Common implementations of functions are currently put into a separate
;; namespace and then referenced explicitly from the resource implementations.
;; Probably it will make sense to continue doing this when switching to
;; protocols, but there is also the possibility of extending Object with the
;; default implementations to avoid having to make explicit references in
;; every resource definition.
;;

(defprotocol IResource
  "Collection of functions to be implemented by a web resource"

  ;;
  ;; These functions are currently implemented as compile-time constants
  ;; The code that dynamically loads the resources, checks for the existence
  ;; of these constants when "linking" the resource into the web application
  ;; routing.  The constants are also use when generating the resource
  ;; representations.
  ;;
  ;; These resources could be replaced with conventions that generate the
  ;; necessary values from the last element of the resource's namespace.
  ;; E.g. from the 'com.sixsq.slipstream.ssclj.resources.connector-template'
  ;; namespace, the following values could be inferred:
  ;;
  ;;  * resource-name = 'ConnectorTemplate'
  ;;  * collection-name = 'ConnectorTemplateCollection'
  ;;  * resource-url = 'connector-template'
  ;;  * resource-tag = 'ConnectorTemplates'
  ;;  * resource-uri = '<prefix>/ConnectorTemplate'
  ;;  * collection-uri = '<prefix>/ConnectorTemplateCollection'
  ;;
  ;; If convention is used, then special cases may need to be taken care of
  ;; for irregular plural forms or for cases like the CloudEntryPoint where
  ;; there is no associated collection.
  ;;
  (resource-name [_]
    "Provides the Pascal-case name of the resource (e.g. ConnectorTemplate).
     This is used in the XML representation of the resource and in the type
     URI.")
  (collection-name [_]
    "Provides the Pascal-case name of the collection for this resource. This
     is usually just the resource name of the resource with 'Collection'
     appended (e.g. ConnectorTemplateCollection). Not all resources have an
     associated collection (e.g. CloudEntryPoint).")
  (resource-url [_]
    "Provides the relative URL for the resource/collection on the server using
     kebab-case (e.g. 'connector-template' for the 'ConnectorTemplate'
     resource.")
  (resource-tag [_]
    "Provides the name of the key (in Pascal-case) used in the CloudEntryPoint
     and in the collection representation for this resource. This is the plural
     form of the resource name (e.g. 'ConnectorTemplates' for the
     'ConnectorTemplate' resource.")
  (resource-uri [_]
    "Provides the unique URI that identifies the type of this resource. This
     is the resource name with a given URI prefix prepended. For CIMI
     resources, the URI prefix is that defined in the standard. For
     non-standard resource, any unique URI prefix can be used. (E.g.
     'http://slipstream.sixsq.com/schema/1/ConnectorTemplate')")
  (collection-uri [_]
    "Provides the unique URI that identifies the type of this resource. This
     is the collection name with a given URI prefix prepended. For CIMI
     resources, the URI prefix is that defined in the standard. For
     non-standard resource, any unique URI prefix can be used. (E.g.
     'http://slipstream.sixsq.com/schema/1/ConnectorTemplateCollection')")

  ;;
  ;; This provides the list of (compojure) routes for this resource.  Currently
  ;; because multimethods that route on the resource-name, resources that follow
  ;; the pattern 'resource-name/UUID/action', do not define this var.  For a
  ;; protocol implementation, all resources will have to define their routes
  ;; explicitly.
  ;;
  ;; Routing is currently done with compojure, but alternatives, such as bidi,
  ;; which uses a data representation for routes, may be better for the case
  ;; where the routes are returned by a function rather than pulled from a var.
  ;;
  ;; The routes provide the concrete mapping between the HTTP request actions and
  ;; the SCRUD functions.  Generally, the HTTP actions are expected to work as in
  ;; any standard REST API. See Section 4 of the CIMI specification for detailed
  ;; information about the expected mapping.
  ;;
  (routes [_]
    "Provides the list of (compojure) routes for this resource.")

  ;;
  ;; Performs any initialization required by the resource.  For example,
  ;; ensuring that particular templates exist in the database.
  ;;
  (initialize [_]
    "Performs any initialization required by the resource, such as creating a
     singleton instance or registering templates for the resource. This should
     be a no-op, if no initialization is needed.")

  ;;
  ;; All of the SCRUD-related actions take an argument (usually a ring request)
  ;; and return a JSON ring response.  When errors occur, exceptions should be raised
  ;; with a map containing a relevant message, HTTP status code, and the resource
  ;; that was concerned.
  ;;
  ;; Generally, the functions map to the HTTP actions as expected for REST APIs.
  ;; See Section 4 of the CIMI specification for the common, expected behavior
  ;; for all CIMI resources.  Details for a specific resource can be found
  ;; in the section describing the schema of that resource.
  ;;
  (query [_ request]
    "Uses the information in the given ring request to return a list of
     resources satisfying the specified criteria. This may be limited with the
     CIMI range parameters or CIMI filters. Associated with HTTP GET requests
     on the collection URL.")
  (add [_ request]
    "Takes the information in the given ring request and creates a new
     instance of this resource. If the resource already exists, then this
     function must raise an exception with a relevant HTTP status code.
     Associated with HTTP POST requests.")
  (retrieve-by-id [_ resource-id]
    "Retrieves a given resource instance associated with the given, unique
     resource ID. Associated with HTTP GET requests.")
  (retrieve [_ request]
    "Retrieves a given resource instance based on the information in the ring
     request. This is a convenience method that will extract the resource ID
     from the request and call the retrieve-by-id function. Associated with
     HTTP GET requests on the resource URL.")
  (edit [_ request]
    "Updates a particular resource instance identified through the request
     with the updated information in the request. If the resource does not
     exist, then this function must throw an exception. Associated with HTTP
     PUT requests.")
  (delete [_ request]
    "Deletes a particular resource instance identified by the request. If the
     resource does not exist, then the function must throw an exception as well
     as when the resource could not be deleted (e.g. because of lack of
     authorization).  Associated with HTTP DELETE requests.")

  ;;
  ;; CIMI allows resources to have specialized actions in addition to the standard
  ;; SCRUD actions (e.g. suspending a VM).  This method is the entry point for
  ;; performing those actions.
  ;;
  (do-action [_ request]
    "Perform a specific action related to the resource. The available actions
     and the semantics of those actions are defined by the resource itself.")

  ;;
  ;; Validates the resource against the defined schema.  It may make sense to
  ;; expose the schema itself to allow for a generalized implementation of this
  ;; function.
  ;;
  (validate [_ resource]
    "Validates the given resource against the defined schema for the resource.
     If the resource is not valid, then an exception must be thrown. If the
     resource is valid, then the function must simply return the resource
     itself.")

  ;;
  ;; These utility functions allow the standard SCRUD (and other functions) to
  ;; be generic. These are not intended to be used by clients of the protocol and
  ;; perhaps should be split into a separate protocol.
  ;;
  (new-identifier [_ json resource-name]
    "Generates a unique, unused identifier for the given resource. This
     normally just returns a random UUID with the resource name prepended.
     However, some resources may follow a different semantic where the
     identifier will depend on the contents of the resource.")
  (set-operations [_ resource]
    "Adds or updates the operations available for the given resource based on
     the user's authorizations and the type of resource.")
  (add-acl [_ json request]
    "Adds the ACL for the resource, potentially based on the user's identity
     or authorizations.")

  )

