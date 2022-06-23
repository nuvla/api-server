# Changelog

## Unreleased

### Added

- Group - Allow group to create subgroups

### Changed

- Group - By default add connected user into created group members
- Time - Helper truncate to days
- Session - Get peers get also users of subgroups
- Subscription of root group is reused in sub-group
- Deployment - Fix interacting with non-free resources
- Group - Bulk delete removed not needed
- Session - Deprecate groups attribute in session and cookie
- Session - New operation `get-groups` added. This operation allow user to get
  all groups hierarchies that he is part of
- Session - Operation `switch-group` allow user to switch to subgroups
  that he is implicitly part of

## [5.25.2] - 2022-05-12

### Changed

- Time - support months duration-unit function

## [5.25.1] - 2022-05-12

### Added

- Email - Add email content for coupons expiry

### Changed

- Nuvlabox - normalize acl on edit

## [5.25.0] - 2022-04-29

### Added

- Test helper - Resource creation
- Event - New `email` category
- Crud - Helper do-action-as-admin and edit-by-id-as-admin
- Pricing - New list subscription added to the protocol
- Nuvlabox - suspended state and unsuspend operation
- Module - follow-customer-trial attribute added. This attribute will allow
  vendor to request module trial period to be same as nuvla trial period
- Tests - optimise tests runtime

### Changed

- Subscription active check is done on most of deployment and nuvlabox actions
- Nuvlabox - Operations map and actions reuse same logic. Fix existing
  divergence.
- Email utils - send-email error message mismatch in some cases with the real
  error
- Test fixture with-existing-user deleted and replaced by a function
- Email - New way to build html and text email
- Configuration - Nuvla config load of stripe enhanced
- Dependencies - Update to parent 6.7.11
- Dependencies - Update to ring 2.0.8
- Nuvlabox - call is executed only when operation is available
- Nuvlabox status - edit is rejected when parent nuvlabox is in suspended state
- Nuvlabox peripheral - add, edit and operations are rejected when parent
  nuvlabox is in suspended state

## [5.24.0] - 2022-03-07

### Added

- Nuvlabox - Use resource log resource
- Deployment - Use resource log resource
- Resource Log - new `resource-log` for recording logs

### Changed

- Fix date convertion from utc
  string [#650](https://github.com/nuvla/api-server/issues/650)
- Cleaned up dead code server
  start [#648](https://github.com/nuvla/api-server/issues/648)
- Two factor authentication - Bigger email token (6 digits)
- Two factor authentication - Support method TOTP
- NuvlaBox Cluster - improved cluster update workflow, with added status notes
  for clarity.
- Kafka - Allow loading Kafka producer conditionally based on
  KAFKA_PRODUCER_INIT env var. Added an option to configure Kafka client from
  env vars. Bumped up Kafka client's version to 0.1.25.
- Elasticsearch - load sniffer conditionally based on env var ES_SNIFFER_INIT.

## [5.23.2] - 2022-02-04

### Changed

- OIDC user register regression redirect url mismatch bugfix

## [5.23.1] - 2022-02-04

### Changed

- OIDC user register regression fix
- NuvlaBox - new operation `generate-new-api-key` to generate a new API key
  during the lifetime of the NuvlaBox

## [5.23.0] - 2022-01-14

### Added

- User - Two factor authentication support
- NuvlaBox Playbooks - new `nuvlabox-playbook` resource for remote management of
  the NuvlaBox device

### Changed

- Callback - Support for multiple tries
- Routes - Remove special user resource route
- User - Authenticated-user view-meta on user resource removed from default ACL
- User - User is not allowed to edit his state
- Configuration - Nuvla `authorized-redirect-urls` configuration
- NuvlaBox - new operation `assemble-playbooks` for staging playbooks for
  execution
- NuvlaBox - new operation `enable-emergency-playbooks` for enabling the one-off
  execution of emergency playbooks
- User, Session, Group - Check if redirect-url is authorized
- Cloud-entry-point - Minor changes
- NuvlaBox Peripheral - fix default ACL for schema validation when payload is
  empty

## [5.22.0] - 2021-12-16

### Added

- ES - Support for `geo-shape` queries
- Data - Support for `geo-shape` type

### Changed

- Nuvlabox-status - Bug fix related to online flag
- Data-record - New `geometry` attribute of type `geo-shape`
- ES - reversed close order of ES client and sniffer

## [5.21.0] - 2021-12-14

### Added

- Middleware - support content-encoding gzip when sent by http client

## [5.20.0] - 2021-12-09

### Added

- System group `group/nuvla-vpn`

### Changed

- Nuvlabox status - Update heartbeat even if NuvlaBox encounter a spec
  validation issue
- Nuvlabox-status - Use delete attributes
- Job - Use delete-attributes
- Nuvlabox - Delete location possible

## [5.19.1] - 2021-11-10

### Changed

- Pricing regression fix

## [5.19.0] - 2021-11-08

### Added

- Nuvla api test jar artifact

### Changed

- Job - Add job to ES at last moment, and add zk path to job tags
- Nuvlabox - Online and inferred location refactor
- Dynamic load namespace helper
- Pricing related resources moved to another artifact

## [5.18.0] - 2021-10-28

### Changed

- Let NuvlaBox workers force update the nuvlabox-cluster they belong to via
  commissioning
- Nuvlabox - Reboot and SSH related actions check NuvlaBox capabilities
- Allow to set advertised address when forcing a new Swarm cluster on a NuvlaBox
- Commission and activate actions on NB does not update updated field bugfix

## [5.17.0] - 2021-10-21

### Added

- Credential template for Openstack infrastructure services

## [5.16.0] - 2021-10-12

### Changed

- Support multiple instance of OIDC
- Allow back get-context operation on deployment_state_10 and
  deployment_state_60
- Copy inferred-location from nuvlabox-status to nuvlabox resource.

## [5.15.0] - 2021-08-04

### Changed

- Remove specific project related conditions
- Updated SixSq legal status (Sarl to SA)
- Group - Group has at least view-meta on itself
- Session - Get peers operation
- User email invitation - invited by changed from active claim to user-id (
  logged in user)
- Group - Invite by email action
- Deployment - get-context deployment state align with distributor
- NuvlaBox Status - add attribute kubelet-version and container-plugins
- NuvlaBox Cluster - let NBs delete a cluster

## [5.14.2] - 2021-06-09

### Changed

- Event - User event bug fix

## [5.14.1] - 2021-06-09

### Changed

- Deployment - Denormalization regression fix

## [5.14.0] - 2021-06-04

### Added

- Credential - gpg key

### Changed

- NuvlaBox Status - add container-stats attribute
- NuvlaBox Status - add optional attribute for temperatures
- Retrieve-by-id check acl
- Event - Enhancement
- Deployment - Bulk-force-delete
- Deployment - force-delete action
- Deployment - Bulk-stop
- Module - Fix boolean type spec
- Spec-tools - Fix broken changes
- Dependencies - Update sixsq.nuvla/parent 6.7.5

## [5.13.1] - 2021-05-18

### Changed

- Nuvlabox - Cluster action acl bugfix

## [5.13.0] - 2021-05-06

### Changed

- Deployment - Keep versions map in deployment module
- Automated commissioning of NuvlaBox clusters
- NuvlaBox Status - expand CPU metrics

## [5.12.2] - 2021-04-30

### Changed

- Module - Retrieve project 500 fix
- Group - users members get view-meta right automatically

## [5.12.1] - 2021-04-28

### Changed

- Apps and Deployment - Apps can ask for a credential with user permissions
- Deployment - Denormalize nuvlabox name, credential name, infrastructure name
- Clj kondo - Reported errors fix
- Session oidc - Bugfix session oidc

## [5.12.0] - 2021-04-09

### Added

- Module - Publish version support
- Deployment - Support bulk update
- Bulk - Bulk operation support
- NuvlaBox Cluster - new resource

### Changed

- User, session and configuration OIDC support fixed predefined redirect-url
- Header authentication extended to support active-claim

## [5.11.1] - 2021-03-22

### Changed

- Deployment - Extend scope of deployment credential for gnss groups
- Nuvlabox - Allow Nuvlabox credential to edit job resource for reboot action
- Nuvlabox - On commission allow Nuvlabox to remove resources

## [5.11.0] - 2021-03-08

### Changed

- Nuvlabox - propagate nuvlabox status online field to nuvlabox on change
- Deployment - Bugfix deployment shouldn't fail on edit when credential doesn't
  exist
- Subscriptions to notifications - Subscription configuration and individual
  subscriptions with lists of notification methods. The expansion of the methods
  was moved to ksqlDB.

## [5.10.0] - 2021-02-22

### Changed

- Nuvlabox status - online-prev flag to keep previous NB online value
- Deployment log - Bugfix deployment log need job get-context
- Nuvlabox status - Next-heartbeat set 2 * refresh-interval + 10s
- Deployment - Bugfix always recreate deployment creds at start
- Nuvlabox - Update operation support payload
- Voucher - Decommission voucher resources

## [5.9.0] - 2021-02-16

### Changed

- Nuvlabox - Update nuvlabox set job execution-mode depending on nuvlabox
  capabilities
- Job - Log zookeeper path at INFO level

### Added

- Notifications with Kafka.

## [5.8.0] - 2021-02-09

### Added

- NuvlaBox Status - new attribute online
- NuvlaBox Status - new attribute to report Docker Swarm certificates expiration
  date
- Job - do not allow edit of target-resource and action
- Deployment parameters - Allow deployment token to update deployment parameters
- Deployment - Allow deployment token to update deployment
- Deployment - Reduce scope of api keys
- Nuvlabox - Add infrastrure-services-coe
- Credential - fix issue mapping creation and update at server startup time
- Nuvlabox status - fix spec
- Job - Add new attributes for supporting job execution in pull mode
- NuvlaBox and Infrastructure Service - Add new capabilities attribute

### Changed

- Deployment parameter - On deployment acls change, acls are propagated to
  corresponding deployment parameters
- Email template - Generate copyright year from current date
- Credential - fix issue mapping creation and update at server startup time
- Nuvlabox status - fix spec
- Credential - status can now also be UNKNOWN

## [5.7.0] - 2020-12-10

### Changed

- NuvlaBox Status - new attributes for NuvlaBox Engine installation parameters
- NuvlaBox - new action for updating the NuvlaBox Engine
- Nuvlabox - add generation ssh key fix (SixSq/tasklist#1921)
- NuvlaBox Status - Add attribute for energy telemetry

## [5.6.0] - 2020-12-07

### Added

- BUILD - Support for Github actions
- NuvlaBox - Add Kubernetes IS support to NuvlaBox commissioning

### Changed

- Deployment - Update action get new module price and license
- Deployment - Remove fetch action

## [5.5.0] - 2020-11-16

### Added

- Vulnerabilities - new resource to act as a database of vulnerabilities to be
  matched against NuvlaBox security scans
- Configuration - new Nuvla configuration attribute to specify the Git
  repository where to find the list of vulnerability databases
- NuvlaBox Peripheral - new attributes for handling network devices

### Changed

- Resource-metadata - Add metadata for templated resources and spec fixes and
  reorganisation
- Customer - List invoices, due date format fix

## [5.4.0] - 2020-10-28

### Changed

- Configuration nuvla - new conditions-url attribute
- Email utils - Send html instead of raw text
- Session template - reset password template removed
- Reset password and user invitation workflow enhancement

## [5.3.0] - 2020-10-09

### Added

- Hook - New non-standard cimi resource that provides an access for events
  driven workflows
- Vendor - New resource allowing a user to create a Stripe Express account

### Changed

- Credential - Remove subscription requirement for VPN
- Customer - Save subscription id in customer resource
- Deployment - New upcoming invoice operation
- Deployment - Subscription creation
- Module - New licence and price attributes
- Configuration - Nuvla get new stripe-client-id attribute
- Nuvlabox status - New optional container-plugins attribute
- Nuvlabox peripheral - schema accept peripheral telemetry
- Deployment - Add check-dct action on deployment
- Nuvlabox status - Inferred-location attribute
- Nuvlabox status - New optional container-plugins attribute
- NuvlaBox status - New optional attribute for vulnerability scans

## [5.2.0] - 2020-09-04

### Changed

- New optional nuvlabox-status attribute for GPIO telemetry
- New optional nuvlabox-engine-version attribute in nuvlabox-status
- Customer - Susbscription send invoices by default
- Switch group - allow switch to group/nuvla-admin user super has to do a switch
  to do administative tasks
- Customer - Make email for group at creation time mandatory
- Customer - Consider past due status subscription as active

## [5.1.0] - 2020-07-31

### Added

- New cimi common attributes to store user-id
  (created-by, updated-by)

### Changed

- Deps - Update cc.qbits/spandex 0.7.5
- Session - Switch group implementation replace claim operation
- Edit implementation for NuvlaBox Peripherals, to restart NuvlaBox data gateway
  streaming based on updated attributes

## [5.0.0] - 2020-07-06

### Added

- New credential type "ssh-key"
- New action "add-ssh-key" for NuvlaBox resource
- New action "revoke-ssh-key" for NuvlaBox resource
- Customer resource
- Pricing resource
- Stripe wrapper library
- Provisioning of Docker Swarm and Kubernetes on AWS, Azure, Google, and
  Exoscale.

### Changed

- Dependency - use temporary spandex 0.7.5-SNAPSHOT
- Deployment, NuvlaBox, Credential subtype VPN return 402 HTTP code when stripe
  configured and user doesn't have an active subscription
- Deployment - new owner and infrastructure service attribute
- Configuration nuvla got a new stripe-api-key attribute
- Nuvlabox status - fix bug in spec causing stacktrace during update of ES
  mapping
- Let users create deployment parameters

## [4.2.16] - 2020-05-12

### Changed

- Deployment - fix regression in deployment created from module

## [4.2.15] - 2020-05-11

### Added

- Session - Implementation of claim action to allow user to act as a group.
- Added new generic attribute `additional-assets`
  to nuvlabox-peripheral resource schema.

### Changed

- Deployment - Stopped deployment can fetch last version of used module.
- Deployment - Allow restart of deployment in stopped state
- Deployment - Allow creation of deployment from another one.
- External authentication - change error message when an account already exist.
- NuvlaBox - User isn't allowed anymore to set owner field.

## [4.2.14] - 2020-04-14

### Added

- New telemetry attributes for the NuvlaBox status

### Changed

- Data-object - Fix regression in S3 because of missing dependency Joda-time
- User creation show clear conflict message when user-identifier already exist.

## [4.2.13] - 2020-03-27

### changed

- Module - path is no more mandatory when editing module
- Module - new attributes valid and validation-message
- Module - allow edit module metadata without updating module-content by
  ommitting the module-content in the body of the request. This will also
  prevent creation of useless versions
- Module - subtype application support for check-docker-compose action
- Module - subtype application support for server managed attributes for
  compatibility and unsupported-options
- Deployment - Spec change replace data-records-filter by data map
- User minimum - Create a validated email for external auth when available
- Deployment - When module doesn't exist return 400 instead of 500
- NuvlaBox peripheral - enable/disable stream action for video class peripherals
- Pagination - Change error message when first and last beyond 10'000
- Voucher - only map country-name if country code exists

## [4.2.12] - 2020-03-06

### Added

- NuvlaBox release - new resource
- Module - new attributes for app compatibility and supported options

### changed

- Deployment - add data-records-filter and deprecate data-records and
  data-objects attributes
- Infrastructure resource - added boolean attr for swarm-enabled and online
- NuvlaBox periphal - two new optional attributes for the data gateway
- Credential - check registry credential
- Module component and application support private registries
- NuvlaBox - new actions check-api and reboot on resource
- NuvlaBox status - new attribute nuvlabox-api-endpoint
- Credential - for swarm, create credential check job on add
- Module - on add and edit, check compatibility for Swarm apps

## [4.2.11] - 2020-02-07

### changed

- NuvlaBox - NuvlaBox viewers should have manage right to be able to check swarm
  credential

## [4.2.10] - 2020-02-07

### Added

- Voucher discipline - new resource

### Change

- NuvlaBox - set tags on commission operation
- Voucher - add country mappings
- Voucher - add correlation between voucher and voucher-discipline
- NuvlaBox - make tags attribute editable for users
- NuvlaBox - make location attribute editable for users
- Voucher - schema update
- NuvlaBox - add internal-data-gateway-endpoint attribute
- NuvlaBox status - add topic raw-sample attributes

### Change

## [4.2.9] - 2020-01-23

- Vouchers - make uniqueness depend on supplier instead of platform
- Credential swarm - Add asynchronous operation to check

### Change

## [4.2.8] - 2020-01-10

### Change

- NuvlaBox peripherals - add port to schema
- NuvlaBox peripherals - fix acl on addition
- NuvlaBox - Update all subresources acl when NuvlaBox acl is updated to allow
  to easily share it
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
- Callback - Allow re-execution of callbacks for user-registration,
  reset-password and for email validation.
- Reset password callback expire after 1 day

## [4.2.4] - 2019-09-18

### Change

- Notification for component and component deployment delete notification when
  callback is executed successfully
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

- Added the nuvlabox-peripheral resource and updated the version numbers (v1)
  for the other nuvlabox resources.

### Changed

- Add form support for session and user needed for UI redirection
- Callback - email validation, user email validation and user password reset
  callbacks should be in final state after execution
- Update to parent version 6.6.0 and ring 2.0.2

## [4.2.1] - 2019-07-29

### Added

- Add metadata for data-record-key and data-record-key-prefix resources

### Changed

- Use the id of the metadata resources also for the name
  (with an optional suffix for 'create' templates)
- External authentication resources (GitHub, OIDC, MITREid)
  have been validated server-side.

## [4.2.0] - 2019-07-24

### Added

- Initial port of resources for external authentication (GitHub, OIDC, and
  MITREid)
- Module application support added with docker-compose and files fields to be
  mapped with Docker secrets and configs

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

- Allow deployment resources to be deleted when they are in the ERROR state.
- Change architecture (single value) to architectures (list)
  in module-components and limit values to specified list.
- Switch to UUIDs for identifiers for email, user-identifier, and data-object
  resources rather than MD5 checksums.
- The attribute credential-id has been changed to parent in the deployment
  resource.
- The schema for the deployment-parameter resource has changed the
  deployment/href to parent. The parent, node-id, and name cannot be changed
  after creation.
- Duplicate infrastructure-service-swarm credentials for a NuvlaBox are not
  allowed. Only the credentials created first are retained.
- The nuvlabox-status (version 0) resource will now overwrite the value of the
  next-heartbeat field with the current time
  (from the server) plus the refresh-interval.
- An additional field, current-time, has been added to the nuvlabox-status (
  version 0) resource to allow clock skew to be detected.

## [3.1.0] - 2019-06-07

### Changed

- Change name attributes for NuvlaBox resources to contain abbreviated nuvlabox
  id.
- Allow the owner of a nuvlabox resource to delete it when in the DECOMMISSIONED
  state.
- NuvlaBox commission action will not create duplicate services and credentials
  if it is called multiple times.
- Remove associated deployment-parameters when a deployment is deleted.

## [3.0.0] - 2019-06-03

### Added

- Restart policy parameters to module schema
- Expiry field to job resource
- Nuvlabox and nuvlabox-status resource to allow the registration, management,
  and use of NuvlaBox machines
- Allow resource constraints (CPUs, memory) to be specified for module
  components

### Changed

- Provide better container state information to help with troubleshooting and
  for understanding when the container is operational
- Improve subject and message in email validation requests
- Improve registration errors when there are email/username conflicts with
  existing users
- Continue nuvlabox delete if nuvlabox-status has already been deleted
- NuvlaBox resources without a version should return a 400 code, not 500
- Fix elasticsearch binding when issue occur during the query call
- Rename type field to subtype and put it in as a resource metadata
  (not backward compatible)
- Release script fix

## [2.2.0] - 2019-05-22

### Added

- Add a new user template to allow a user to invite another person to use nuvla

### Changed

- Change schema of infrastructure service credential. The
  infrastructure-services field has been deleted and replaced by parent field.

## [2.1.1] - 2019-05-13

### Changed

- Fix spec of job status message field

## [2.1.0] - 2019-05-06

### Added

- Added resource-metadata information for job resource
- Add notification resource that allows administrators to notify users of
  important events

### Changed

- Update the default user resource ACL to allow all authenticated users to see
  user resource metadata
- Do not overwrite the ACL in the post-add actions for user resources
- Fix a regex problem with dates that have only one digit
- Include the id field in full text searches
- Provide a default value for :name for user resources if not given explicitly (
  defaults to username then email)
- Allow users to search the group collection and provide the view-meta rights
  for all authenticated users

## [2.0.0] - 2019-04-29

Initial, functionally-complete release.

### Changed

- Use java time instead of joda time
- Update ring container to version 2.0.0

## [0.9.0] - 2019-04-17

Test release to verify release process.

### Changed

- Update parent to version 6.3.0.

 
