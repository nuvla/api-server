# Changelog

## Unreleased

### Change

## [4.2.8] - 2020-01-10

### Change

  - NuvlaBox peripherals - add port to schema
  - NuvlaBox peripherals - fix acl on addition
  - NuvlaBox - Update all subresources acl when NuvlaBox acl is updated
    to allow to easily share it
  - Voucher - fix voucher schema add DISTRIBUTED state

## [4.2.7] - 2019-12-09

### Change

  - Update to parent 6.7.2
  - Module - support for applicaiton of subtype kubernetes
  - NuvlaBox - update existing resources on commission
  - NuvlaBox - make NuvlaBox visible for vpn infrastructure when selected
  - Credential openvpn - change error message when duplicated credential
  - Dockerfile update to ring 2.0.4

## [4.2.6] - 2019-11-13

### Change

  - Dockerfile update to ring 2.0.3
  - Generation of VPN credentials
  - Configuration template VPN added
  - Infrastructure service VPN resource added
  - Allow to conifgure ES client for ES cluster and use ES sniffer.
  - Support bulk delete for more resources and fix operations for collection
  - Job - Stale job because of race condition fix
  - NuvlaBox - Simplify names of nuvlabox sub-resources at commission time

## [4.2.5] - 2019-10-10

### Change

  - Data record - Allow bulk delete
  - CIMI - Bulk delete route for collections
  - Group - Allow setting acl at group creation time
  - Session - Extend validity of cookie from 1 day to 1 week
  - Callback - Allow re-execution of callbacks for user-registration, reset-password and for
    email validation.
  - Reset password callback expire after 1 day

## [4.2.4] - 2019-09-18

### Change

  - Notification for component and component deployment delete notification when callback is 
    executed successfully
  - Cloud-entry-point - Duplicate port fix in base uri when used with 
    x-forwarded-host header and dynamic port
  - Deployment log - resource can be deleted by user session

## [4.2.3] - 2019-09-04

### Added

  - Deployment log resource
  - Voucher report - create new voucher-report resource

### Change

  - Container spec - environmental variable spec fix
  - When location is set in map-response, put it also in response json
  - Module component - image update action 
  - Deployment - component image update action
  - Job - Fix spec error when job is not started and duration set to nil
  - Nuvlabox - use name of nuvlabox when user define it in all sub-resources
  - Voucher - add supplier to voucher
  - Create user callback should not fail silently

## [4.2.2] - 2019-08-07

### Added

  - Added the nuvlabox-peripheral resource and updated the
   version numbers (v1) for the other nuvlabox resources.

### Changed

  - Add form support for session and user needed for UI redirection
  - Callback - email validation, user email validation and user 
    password reset callbacks should be in final state after execution
  - Update to parent version 6.6.0 and ring 2.0.2

## [4.2.1] - 2019-07-29

### Added

  - Add metadata for data-record-key and 
    data-record-key-prefix resources

### Changed 

  - Use the id of the metadata resources also for the name
    (with an optional suffix for 'create' templates)
  - External authentication resources (GitHub, OIDC, MITREid) 
    have been validated server-side. 

## [4.2.0] - 2019-07-24

### Added

  - Initial port of resources for external authentication (GitHub,
    OIDC, and MITREid)
  - Module application support added with docker-compose and files 
    fields to be mapped with Docker secrets and configs

## [4.1.0] - 2019-07-11

### Changed

  - User - Do not allow user change his name
  - Module - Conflict if path already exit on add
  - ES mapping generation for double is taken into account 
  - Make double spec accept number as value
  - Upgrade to ring-container 2.0.1 and parent 6.5.1.
  - Remove 'busy' attribute from USB peripheral description

## [4.0.0] - 2019-06-20

### Changed

  - Allow deployment resources to be deleted when they are in
    the ERROR state.
  - Change architecture (single value) to architectures (list) 
    in module-components and limit values to specified list.
  - Switch to UUIDs for identifiers for email, user-identifier,
    and data-object resources rather than MD5 checksums.
  - The attribute credential-id has been changed to parent in 
    the deployment resource.
  - The schema for the deployment-parameter resource has changed
    the deployment/href to parent. The parent, node-id, and name
    cannot be changed after creation.
  - Duplicate infrastructure-service-swarm credentials for a 
    NuvlaBox are not allowed. Only the credentials created first
    are retained.
  - The nuvlabox-status (version 0) resource will now overwrite
    the value of the next-heartbeat field with the current time
    (from the server) plus the refresh-interval. 
  - An additional field, current-time, has been added to the
    nuvlabox-status (version 0) resource to allow clock skew to
    be detected.

## [3.1.0] - 2019-06-07

### Changed

  - Change name attributes for NuvlaBox resources to contain
    abbreviated nuvlabox id.
  - Allow the owner of a nuvlabox resource to delete it when 
    in the DECOMMISSIONED state.
  - NuvlaBox commission action will not create duplicate services 
    and credentials if it is called multiple times.
  - Remove associated deployment-parameters when a deployment
    is deleted. 

## [3.0.0] - 2019-06-03

### Added

  - Restart policy parameters to module schema
  - Expiry field to job resource
  - Nuvlabox and nuvlabox-status resource to allow the registration,
    management, and use of NuvlaBox machines
  - Allow resource constraints (CPUs, memory) to be specified for module
    components

### Changed

  - Provide better container state information to help with troubleshooting
    and for understanding when the container is operational 
  - Improve subject and message in email validation requests
  - Improve registration errors when there are email/username conflicts
    with existing users
  - Continue nuvlabox delete if nuvlabox-status has already been deleted
  - NuvlaBox resources without a version should return a 400 code, not 500
  - Fix elasticsearch binding when issue occur during the query call
  - Rename type field to subtype and put it in as a resource metadata 
    (not backward compatible)
  - Release script fix

## [2.2.0] - 2019-05-22

### Added

  - Add a new user template to allow a user to invite another person 
    to use nuvla

### Changed

  - Change schema of infrastructure service credential. The 
    infrastructure-services field has been deleted and replaced by 
    parent field.

## [2.1.1] - 2019-05-13

### Changed

  - Fix spec of job status message field

## [2.1.0] - 2019-05-06

### Added

  - Added resource-metadata information for job resource
  - Add notification resource that allows administrators to notify users of important events

### Changed 

  - Update the default user resource ACL to allow all authenticated users to see user 
    resource metadata
  - Do not overwrite the ACL in the post-add actions for user resources
  - Fix a regex problem with dates that have only one digit
  - Include the id field in full text searches
  - Provide a default value for :name for user resources if not given explicitly (defaults 
    to username then email)
  - Allow users to search the group collection and provide the view-meta rights for all 
    authenticated users

## [2.0.0] - 2019-04-29

Initial, functionally-complete release. 

### Changed
  - Use java time instead of joda time
  - Update ring container to version 2.0.0

## [0.9.0] - 2019-04-17

Test release to verify release process.

### Changed

  - Update parent to version 6.3.0.

 
