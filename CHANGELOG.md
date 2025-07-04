# Changelog

## Released

## [6.17.0](https://github.com/nuvla/api-server/compare/6.16.3...6.17.0) (2025-06-30)


### Features

* **fetch-coe-resources:** New deployment action fetch-coe-resources ([#1029](https://github.com/nuvla/api-server/issues/1029)) ([25e7287](https://github.com/nuvla/api-server/commit/25e7287f0678346ba796ef9a181b30a8ab187bb2))


### Bug Fixes

* **data-object:** Conflict id should not happen when using different infra-service. ([#1030](https://github.com/nuvla/api-server/issues/1030)) ([de762da](https://github.com/nuvla/api-server/commit/de762da5d238c914ca17f7c7181532cae0542520))

## [6.16.3](https://github.com/nuvla/api-server/compare/6.16.2...6.16.3) (2025-06-17)


### Bug Fixes

* **callback-2fa-deactivation:** Delete credential on deactivation. ([#1024](https://github.com/nuvla/api-server/issues/1024)) ([a82f55d](https://github.com/nuvla/api-server/commit/a82f55d013d57fd056b696b13c4782ecd5d5cdf0))
* **Credential:** Enhance behavior when there is an issue in decryption ([#1026](https://github.com/nuvla/api-server/issues/1026)) ([7af6d11](https://github.com/nuvla/api-server/commit/7af6d11aa01c545ea2e8e3f578df642c416e1684))

## [6.16.2](https://github.com/nuvla/api-server/compare/6.16.1...6.16.2) (2025-06-16)


### Bug Fixes

* **Credential:** Fix retrieve-by-id and query-collection for credential to keep iv ([#1021](https://github.com/nuvla/api-server/issues/1021)) ([ebdd72d](https://github.com/nuvla/api-server/commit/ebdd72d9cd941b44ffb52a3ba54f5a2b586f1949))

## [6.16.1](https://github.com/nuvla/api-server/compare/6.16.0...6.16.1) (2025-06-13)


### Bug Fixes

* **Credential:** Fix retrive-by-id and query-collection for credential ([#1017](https://github.com/nuvla/api-server/issues/1017)) ([b41a08d](https://github.com/nuvla/api-server/commit/b41a08d483e3d9d51f3fa99e584ee1f5e7a0736a))

## [6.16.0](https://github.com/nuvla/api-server/compare/6.15.1...6.16.0) (2025-06-12)


### Features

* **credentials:** Support encryption ([40cd510](https://github.com/nuvla/api-server/commit/40cd5105eb6fdec5a5d9285a1336439a829bc3f3))
* **emailing:** Support OAUTH2 SMTP config, authentication and refresh token in a generic way ([#1011](https://github.com/nuvla/api-server/issues/1011)) ([a31658e](https://github.com/nuvla/api-server/commit/a31658e8c9008f521775a378b831c6384bf0abff))
* **NuvlaEdge:** Coe resource action pull image support for private registries ([03576dd](https://github.com/nuvla/api-server/commit/03576dd7b0941977676dfc289f6401c3361601bc))


### Bug Fixes

* **Credential migration:** Multithread migration to make it run in some minutes instead of hours ([a0ada3e](https://github.com/nuvla/api-server/commit/a0ada3eef255ee5b5026dff859a5d0760c517fc9))
* **NuvlaEdge metrics:** NE should be able to register metrics even if NE system time is wrong ([#1012](https://github.com/nuvla/api-server/issues/1012)) ([d995245](https://github.com/nuvla/api-server/commit/d9952454d03f6d6174e25f3641e97b4a4fbe1a12))

## [6.15.1](https://github.com/nuvla/api-server/compare/6.15.0...6.15.1) (2025-04-07)


### Bug Fixes

* **NuvlaEdge:** Commission coe-list bugfix ([e5ea24e](https://github.com/nuvla/api-server/commit/e5ea24ea309d46d0abb6b81d5203d6e4dc410f83))

## [6.15.0](https://github.com/nuvla/api-server/compare/6.14.0...6.15.0) (2025-02-19)


### Features

* **Deployment Group:** Recompute fleet on edit when dynamic fleet filter changes ([#1003](https://github.com/nuvla/api-server/issues/1003)) ([2a11bc9](https://github.com/nuvla/api-server/commit/2a11bc91deb8328fe4e982adeddc0ed4a5a4d26a))
* **deps:** nuvla ring v2.3.0 ([bcf47d8](https://github.com/nuvla/api-server/commit/bcf47d83db3ed5415247611a835df580335fe351))
* **json logging:** Logging with telemere ([af65589](https://github.com/nuvla/api-server/commit/af6558900aed3c38f3016174d8606deaf73bb2c0))
* **Jsonista:** Replace clojure.data/json by metosin/jsonista for performance purpose ([e4b9457](https://github.com/nuvla/api-server/commit/e4b945715ca4629f2b25f8e53788986908aad89b))
* **NuvlaBox status:** add field "type" to ips of interfaces to distinguish v4 and v6 IPs ([#1000](https://github.com/nuvla/api-server/issues/1000)) ([3eb6a49](https://github.com/nuvla/api-server/commit/3eb6a493cd6044bd078814b2f2d0ed28a82d6586))


### Bug Fixes

* **Notification method test:** Disable not needed kafka fixture to avoid polluting the test logs ([5ce83fb](https://github.com/nuvla/api-server/commit/5ce83fb727fafc27fe9740a0683aa8d35ecbcaa7))

## [6.14.0](https://github.com/nuvla/api-server/compare/6.13.2...6.14.0) (2025-02-07)


### Features

* **App Bouquets:** Copy first apps set subtype to module top level field apps-set-subtype ([#998](https://github.com/nuvla/api-server/issues/998)) ([0610c48](https://github.com/nuvla/api-server/commit/0610c480091db69fc4d1546e3d315e26a1d78dc3))
* **Deps:** Upgrade of dependencies ([#991](https://github.com/nuvla/api-server/issues/991)) ([9243de5](https://github.com/nuvla/api-server/commit/9243de5990398fa520ecfe49f34d25e998099c3c))
* **migration-scripts:** Version control Nuvla migration scripts ([#982](https://github.com/nuvla/api-server/issues/982)) ([76941b7](https://github.com/nuvla/api-server/commit/76941b706ab1461f8a1a315522ce2f26b70671f5))

## [6.13.2](https://github.com/nuvla/api-server/compare/6.13.1...6.13.2) (2025-02-03)


### Bug Fixes

* **Nuvlabox status:** Deployment state jobs piling up when NE is slow or offline bugfix ([#994](https://github.com/nuvla/api-server/issues/994)) ([6eae638](https://github.com/nuvla/api-server/commit/6eae638c2ffdb95932f63b937dfe30990c668d15))

## [6.13.1](https://github.com/nuvla/api-server/compare/6.13.0...6.13.1) (2025-01-31)


### Bug Fixes

* **Deployment group:** Action recompute-fleet must take DG subtype into account ([dda116b](https://github.com/nuvla/api-server/commit/dda116baacb2125a82c7ca7ab1ad37cc20c68585))

## [6.13.0](https://github.com/nuvla/api-server/compare/6.12.2...6.13.0) (2025-01-27)


### Features

* **Deployment:** Support for stop remove-volumes and remove-images params ([#987](https://github.com/nuvla/api-server/issues/987)) ([a6fc40b](https://github.com/nuvla/api-server/commit/a6fc40b6fc4a556a3e7520995561aa784edfd510))


### Bug Fixes

* **Deployment group:** Bulk propagate name change to linked deployments ([#985](https://github.com/nuvla/api-server/issues/985)) ([9d1ce88](https://github.com/nuvla/api-server/commit/9d1ce88bb198aba95aa840a7416f537c9ad518b2))
* **Deployment:** Do not provide payload when null ([a6fc40b](https://github.com/nuvla/api-server/commit/a6fc40b6fc4a556a3e7520995561aa784edfd510))
* **ES binding:** Param name format change to support special characters in parameter name ([9d1ce88](https://github.com/nuvla/api-server/commit/9d1ce88bb198aba95aa840a7416f537c9ad518b2))
* **NuvlaEdge:** Bulk propagate name change to linked deployments ([9d1ce88](https://github.com/nuvla/api-server/commit/9d1ce88bb198aba95aa840a7416f537c9ad518b2))

## [6.12.2](https://github.com/nuvla/api-server/compare/6.12.1...6.12.2) (2024-12-24)


### Bug Fixes

* **Nuvlabox status:** create deployment_state job for old docker version bugfix ([6d79a8b](https://github.com/nuvla/api-server/commit/6d79a8b3c304ec1d9d4c184d7387b0ff6a639bc9))

## [6.12.1](https://github.com/nuvla/api-server/compare/6.12.0...6.12.1) (2024-12-23)


### Bug Fixes

* **Nuvlabox status:** create deployment_state job for old docker version ([#979](https://github.com/nuvla/api-server/issues/979)) ([856fce6](https://github.com/nuvla/api-server/commit/856fce6aec3dd08e2636bbc4296d2b1068757481))
* **NuvlaEdge:** Augment "nuvlabox" resource by coe info objects ([#974](https://github.com/nuvla/api-server/issues/974)) ([627e332](https://github.com/nuvla/api-server/commit/627e33236050268bd14b2db17b19db5248ddc9f7))
* **std-crud:** Json patch failing in clojure libs ([#980](https://github.com/nuvla/api-server/issues/980)) ([2363d6b](https://github.com/nuvla/api-server/commit/2363d6b73e21bcc4d6649cd641f366ebea7d015a))
* **wrong_infra_selected:** Added field subtype to DG ([#975](https://github.com/nuvla/api-server/issues/975)) ([07ca652](https://github.com/nuvla/api-server/commit/07ca65248c4518c8e3b7f735213eb9f6b4d552a4))

## [6.12.0](https://github.com/nuvla/api-server/compare/6.11.3...6.12.0) (2024-12-13)


### Features

* **Nuvlabox status:** Create deployment state job when corresponding coe-resources is not present ([8bd843d](https://github.com/nuvla/api-server/commit/8bd843daddc2551e02a2e7127fa0aed553261888))
* **Nuvlabox status:** Extract and build deployment parameters from NuvlaEdge telemetry ([#957](https://github.com/nuvla/api-server/issues/957)) ([8bd843d](https://github.com/nuvla/api-server/commit/8bd843daddc2551e02a2e7127fa0aed553261888))


### Bug Fixes

* **Deployment group:** Allow user manage created jobs ([#972](https://github.com/nuvla/api-server/issues/972)) ([147bc4a](https://github.com/nuvla/api-server/commit/147bc4aa9ede825687a0f25bf2b424785f79e81b))
* **Deployment group:** Make jobs created by auto update visible to the dg owner ([#969](https://github.com/nuvla/api-server/issues/969)) ([3e1c584](https://github.com/nuvla/api-server/commit/3e1c584c615bda483da6c495ce69dd4ac1ac9eca))
* **Deployment set:** Add lifecycle test auto update with dynamic filter ([2ec5da4](https://github.com/nuvla/api-server/commit/2ec5da48bf7a1cb6dbbbf8b760d7ef24aa36b6fb))
* **Deployment set:** Auto update optimization avoid multiple read and write of deployment set to unneed queries ([2ec5da4](https://github.com/nuvla/api-server/commit/2ec5da48bf7a1cb6dbbbf8b760d7ef24aa36b6fb))
* **Deployment set:** Auto update should not bypass state-machine and call update move state to updating state ([2ec5da4](https://github.com/nuvla/api-server/commit/2ec5da48bf7a1cb6dbbbf8b760d7ef24aa36b6fb))
* **Deployment set:** Auto update should not bypass state-machine and call update move state to updating state ([2ec5da4](https://github.com/nuvla/api-server/commit/2ec5da48bf7a1cb6dbbbf8b760d7ef24aa36b6fb))
* **Deployment set:** Recompute fleet reuse existing code instead of a non standatd write ([2ec5da4](https://github.com/nuvla/api-server/commit/2ec5da48bf7a1cb6dbbbf8b760d7ef24aa36b6fb))
* **Nuvlabox status:** Allow empty string for cluster node label value ([8bd843d](https://github.com/nuvla/api-server/commit/8bd843daddc2551e02a2e7127fa0aed553261888))

## [6.11.3](https://github.com/nuvla/api-server/compare/6.11.2...6.11.3) (2024-11-26)


### Bug Fixes

* **Std-crud:** Json patch break when keys are interpreted as namespaced keys bugfix ([#966](https://github.com/nuvla/api-server/issues/966)) ([5adae42](https://github.com/nuvla/api-server/commit/5adae429f2aaaebfcd0aa4738f44483c58e8a869))

## [6.11.2](https://github.com/nuvla/api-server/compare/6.11.1...6.11.2) (2024-11-22)


### Bug Fixes

* **Deployment:** Checks for subscription and access to cres and infras should be done as owner of the deployment ([b73b630](https://github.com/nuvla/api-server/commit/b73b6306b01898b6869ee04b539578d7833cd8de))

## [6.11.1](https://github.com/nuvla/api-server/compare/6.11.0...6.11.1) (2024-11-21)


### Bug Fixes

* **dg-permission-issues:** At creation time DG acl is built to include the DG id in edit-data/manage and the owner of it should be in acl edit-data/view-acl/delete/manage ([110c9ef](https://github.com/nuvla/api-server/commit/110c9efde35837cacef6d8502779894ca92dfe22))
* **dg-permission-issues:** At creation time of DG save the creator of it in an owner attribute ([110c9ef](https://github.com/nuvla/api-server/commit/110c9efde35837cacef6d8502779894ca92dfe22))
* **dg-permission-issues:** put authn info as job payload ([110c9ef](https://github.com/nuvla/api-server/commit/110c9efde35837cacef6d8502779894ca92dfe22))
* **dg-permission-issues:** recompute fleet as owner and fix tests ([110c9ef](https://github.com/nuvla/api-server/commit/110c9efde35837cacef6d8502779894ca92dfe22))

## [6.11.0](https://github.com/nuvla/api-server/compare/6.10.0...6.11.0) (2024-11-12)


### Features

* **Deployment group:** add support for dg auto update custom interval ([378af5e](https://github.com/nuvla/api-server/commit/378af5e3342a3026dc6030545bba977a28dd6a1e))
* **dg-no-apps-no-edges:** Allow DGs to have no edges and / or no apps defined ([#956](https://github.com/nuvla/api-server/issues/956)) ([884ec67](https://github.com/nuvla/api-server/commit/884ec6766eefaf3e561de85424012f1a0f12eb69))


### Bug Fixes

* **Deployment group:** Add api-endpoint attribute ([02455f7](https://github.com/nuvla/api-server/commit/02455f705058ca713d4a4caa9b8bf1aac79cea1d))
* **Deployment:** Allow user to edit api-endpoint attribute ([02455f7](https://github.com/nuvla/api-server/commit/02455f705058ca713d4a4caa9b8bf1aac79cea1d))
* **Nuvlabox:** Make coe resource action available only for version &gt;= v2.17.0 ([#954](https://github.com/nuvla/api-server/issues/954)) ([4ca2d02](https://github.com/nuvla/api-server/commit/4ca2d0204c6a85e697a277e65a4b4c4c5f470a04))
* **std-crud:** Refactor and document std-crud/add-fn and std-crud/edit-fn ([46e6231](https://github.com/nuvla/api-server/commit/46e62319d276e0607ea931e9a3db61e840c9747c))

## [6.10.0](https://github.com/nuvla/api-server/compare/6.9.1...6.10.0) (2024-10-15)


### Features

* **Nuvlabox resource:** Support for coe resource actions operation ([ecc4135](https://github.com/nuvla/api-server/commit/ecc41354beeb1cde0223aa33320f9bce82aa2062))


### Bug Fixes

* **Github workflow:** Install lein missing in ubuntu 24 ([#947](https://github.com/nuvla/api-server/issues/947)) ([5a08748](https://github.com/nuvla/api-server/commit/5a08748907d462ef3b48b3e061a80315c91023f9))
* **min-req-archs:** arch64 -&gt; aarch64 ([#949](https://github.com/nuvla/api-server/issues/949)) ([459bc15](https://github.com/nuvla/api-server/commit/459bc150f4286a2005bbf4b22c309b94492b6662))
* **module spec:** add missing architectures ([0eb0522](https://github.com/nuvla/api-server/commit/0eb0522fb1d897b0fe4b2340327aa61f1db6c6b1))

## [6.9.1](https://github.com/nuvla/api-server/compare/6.9.0...6.9.1) (2024-10-03)


### Bug Fixes

* **Deployment:** Get context return minimal nuvlabox status ([#942](https://github.com/nuvla/api-server/issues/942)) ([6d85102](https://github.com/nuvla/api-server/commit/6d851023a32841597841d221bbc39bdd66b353ea))

## [6.9.0](https://github.com/nuvla/api-server/compare/6.8.1...6.9.0) (2024-09-23)


### Features

* **dg-min-req:** cleanup, additional tests ([a5bc70b](https://github.com/nuvla/api-server/commit/a5bc70b36486fd9764c8b10aaf6cde660180b02f))
* **Module:** App minimum requirements + resource check at deployment time ([#931](https://github.com/nuvla/api-server/issues/931)) ([6cf45c4](https://github.com/nuvla/api-server/commit/6cf45c4e53500e0b3334f7affb7e846451a05cda))
* **Nuvlabox:** Bulk update support ([#935](https://github.com/nuvla/api-server/issues/935)) ([7ca78d2](https://github.com/nuvla/api-server/commit/7ca78d2141602385593197c00e0406ef3bea78e3))
* **Std-crud:** Support json patch edit format ([#938](https://github.com/nuvla/api-server/issues/938)) ([2432009](https://github.com/nuvla/api-server/commit/24320094c22b9c13a1d9f164237cfbe1535b5f32))


### Bug Fixes

* **Common utils:** Enhance cannot do action because of state error message ([7ca78d2](https://github.com/nuvla/api-server/commit/7ca78d2141602385593197c00e0406ef3bea78e3))
* **Deployment:** Bulk delete support([#937](https://github.com/nuvla/api-server/issues/937)) ([08a58ab](https://github.com/nuvla/api-server/commit/08a58ab0ecbcac49a2d99f715b39c46623efe39c))
* **Deployment:** Delete operation should not be available to deployment in ERROR state ([08a58ab](https://github.com/nuvla/api-server/commit/08a58ab0ecbcac49a2d99f715b39c46623efe39c))
* **dg-min-req:** add back action map, used to generate resource-metadata ([745c951](https://github.com/nuvla/api-server/commit/745c951b5b3991d1a6b48e17baec0bddbb6dbc63))
* **dg-min-req:** check disk with maximum available space ([2028b27](https://github.com/nuvla/api-server/commit/2028b27b65d7512608239eb77f7aae1231293abd))
* **Job:** Allow user to cancel created bulk jobs ([7ca78d2](https://github.com/nuvla/api-server/commit/7ca78d2141602385593197c00e0406ef3bea78e3))
* **Job:** Move job utils and create new utils namespace and refactor to reuse code ([7ca78d2](https://github.com/nuvla/api-server/commit/7ca78d2141602385593197c00e0406ef3bea78e3))
* **Job:** Set created-by attribute as user at the origin of the creation of the job ([7ca78d2](https://github.com/nuvla/api-server/commit/7ca78d2141602385593197c00e0406ef3bea78e3))
* **nsorg:** Namespace ordering ([c52e86f](https://github.com/nuvla/api-server/commit/c52e86f9090e5ecb10cc32ebe0d6d25a9a489ca0))
* **utils:** Add Secure rand nth function ([#933](https://github.com/nuvla/api-server/issues/933)) ([0a2623b](https://github.com/nuvla/api-server/commit/0a2623b1b578fdd023d22548113122811aba97d8))

## [6.8.1](https://github.com/nuvla/api-server/compare/6.8.0...6.8.1) (2024-08-07)


### Bug Fixes

* **Deployment parameter:** Allow empty values ([48c1c1c](https://github.com/nuvla/api-server/commit/48c1c1c2f32d0a33aae91ca013d3fe986bc660a0))
* **job:** Do not fail when status message is too big. Instead truncate it 100k chars ([#929](https://github.com/nuvla/api-server/issues/929)) ([aa7f821](https://github.com/nuvla/api-server/commit/aa7f821e9082bb1ebe41902951a8a55d02a4d790))
* **README.md:** Fix build badges ([a4f33ef](https://github.com/nuvla/api-server/commit/a4f33ef37ef7025e580cb410b389cc99c25710ff))


### Continuous Integration

* **release-please:** Remove bootstrap-sha ([a4f33ef](https://github.com/nuvla/api-server/commit/a4f33ef37ef7025e580cb410b389cc99c25710ff))

## [6.8.0](https://github.com/nuvla/api-server/compare/v6.7.1-SNAPSHOT...6.8.0) (2024-07-29)


### Features

* **module:** add helm repo creds and module application Helm ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **nuvlabox-status:** add new containers stats attributes ([28bbeba](https://github.com/nuvla/api-server/commit/28bbeba7c85611e1d0feda82a92acc60e50daebb))
* **nuvlabox-status:** add new containers stats attributes started-at and cpu-capacity ([#921](https://github.com/nuvla/api-server/issues/921)) ([a362749](https://github.com/nuvla/api-server/commit/a362749bdefc6908e03ee21900ef751a01453757))


### Bug Fixes

* **data-object:** S3 create client bugfix ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **deployment:** Allow search Deployments by NuvlaEdge name (Migration is needed) ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **events:** Support creation of events usefull to create an audit log ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **test:** Flaky test nuvlabox status regarding availability fix ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))


### Continuous Integration

* **ci:** Remove maven ([#925](https://github.com/nuvla/api-server/issues/925)) ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **ci:** Remove unused version script ([043e5fd](https://github.com/nuvla/api-server/commit/043e5fdd1fb0642f00ca295f1dbf324003073504))
* **github:** Github orchestrate build of ui docker image ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **github:** Upgrade plugins ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **release-please:** Release please control version based on commit messages ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))
* **travis:** Remove reference to Travis ([50af1a8](https://github.com/nuvla/api-server/commit/50af1a8b23bc38fe5dabe84415539cd82418a51d))

## [6.7.0] - 2024-06-17

- Notification method - Add mqtt as notif method option
- Infrastructure service - Remove infrastructure service coe support
- Credentials - Remove cloud provider credentials support
- Zookeeper - Allow setting of Zookeeper client creation timeout

## [6.6.4] - 2024-05-23

- nuvlabox data - Total commissioned time computation should take edge creation date into account

## [6.6.3] - 2024-05-16

- nuvlabox-release resource: change query acl to group/nuvla-anon
- nuvlabox data-utils - Fix latest availability query parameter
- Testing - replace curator test zookeeper server by container
- Insert metrics: only return the first error when there are many
- Deployment - module content should not be indexed to allow big docker-compose or manifest
- Deps - force clojure version for repl

## [6.6.2] - 2024-04-11

- contrainer ring upgrade missing fix

## [6.6.1] - 2024-04-10

- dependencies optimization and remove lib with high security flows
  - put test logging into warn mode
  - cleanup and optimize container jars
  - ring upgrade
  - exclude qrcode visual generation from one-time
- module-application - Allow module application content and file-content to be bigger than 32K


## [6.6.0] - 2024-04-08

- JDK 21
  - clojure upgrade and random-uuid and type hints for ssh generation
  - upgrade deps
  - parent dependencies
  - github action warning fix
  - logback test config
  - reorder deps and add exclusions to remove confusion in deps
- Nuvlaedge data
  - Mem optimized version of compute-availabilities
  - Fix inconsistency of dynamic filter definition and number of edges
  - Consider commissioned edges that never sent telemetry as always offline since their creation
  - Let ES compute the aggregation timestamps
  - Add ring-accept library
  - Add library for full support of http Accept header

## [6.5.0] - 2024-03-21

- Nuvlabox data resource to query availability and telemetry
- Nuvlabox insert telemetry and availability into timeseries
- Building and displaying time series in Nuvla
- Deployment group - Add support for file overriding
- CI: use SONARQUBE_TOKEN
- kafka: improve log strings

## [6.4.4] - 2024-02-21

- SERVER: Start nrepl when nrepl-port env is set
- Deployment set - The Updated timestamp of a deployment group gets updated even when nothing actually changes bug fix
- Notification Method - Add test operation that produce a test event
- Deployment - Version update of an existing deployment bugfix. Add lifecycle test
- Deployment-parameter can be created by anyone with any parent (deployment)
- Nuvlabox status - Swarm enabled flag bugfix and add lifecycle test
- general utils - encoding uri component utility fitting best web browser world

## [6.4.3] - 2024-01-18

- Test release. No changes.

## Released

## [6.4.2] - 2024-01-17

- Fix: app config files content gets overwritten.  

## [6.4.1] - 2023-12-21

- Bidnding ES - Disable refresh option
  - Nuvlabox on edit
  - Nuvlabox-status on editDeployment-parameter on edit
  - Event on add
  - Job on edit
  - Deployment-parameter on edit
- Common spec - Relax spec on resource description to allow empty strings
- Deployment set
  - Enhance cancel operation message when no running job
  - Operational status detect duplicated deployments
- NuvlaBox - parent and acl are required for matching for notifications
- Event - make authn info and success attribute optional

## [6.4.0] - 2023-12-14

- Event -  Auto event generation framework plus enabled events on module publication
- Nuvlabox - Pull jobs in state CANCELLED are still given to NuvlaEdges for execution
- Deployment group
  - Allow empty env var value override for app bouquets and DGs
  - Deployment group divergence check should consider deployment with state UPDATED as matching the spec
  - Admin should be able to edit deployment-set objects regardless of its state
  - Support for registries credentials in plan and current-state of deployment
  - Version nil spec issue fix, indirect issue related to deployment immutable fields bug
  - Make recompute fleet operation available on PARTIALLY_STARTED state
- Module - Edit module fetch module refactor
- Es binding - Set default number of shards to 3 and number of replicas to 2
- Deployment
  - Operational status should detect if a NuvlaEdge has been deleted
  - Persist operational status each time user call the action
  - Merge module for files env and params changed to allow remove of variables
  - Application logo in deployment is not updated when module logo change bugfix
  - Stop action accept delete option that will delete deployment 
  when stop action is successful
  - Created jobs acl changed for user
- Zookeeper - Avoid job not reaching zookeeper because of closed client

## [6.3.1] - 2023-12-08

- Nuvlabox-status - detect swarm mode from telemetry (#851) patch

## [6.3.0] - 2023-11-09

- Nuvlabox - Allow set-offline action for suspended state
- Query support matching one or more values for an attribute
- Nuvlabox - NullPointerException on PUT /api/nuvlabox with a filter and select=state (#830)
- Middleware - Parser cimi-params remove support expand non used option
- Middleware - Parser cimi-params remove support for wildcard in select option
- ES binding - Always retrieve id acl state resource-type usually needed
- Std-crud - Return only what user is asking for, plus the id of resources
- Support select param for get queries
- Std-crud - Do not store dynamic user operations on edit
- Common spec - relax operations spec

## [6.2.0] - 2023-10-27

- Nuvlabox - Refactor #801
- Nuvlabox - Heartbeat new action
- Nuvlabox - Set-offline new action
- Nuvlabox - Allow user to edit intervals, check minimum vals for intervals
- Nuvlabox - Set heartbeat-interval to the default value if it doesn't exist
- Nuvlabox - Heartbeat action return last updated document value
- Nuvlabox status - Change tolerance for telemetry
- Nuvlabox status - Last-telemetry and next-telemetry support
- Nuvlabox status - Telemetry online retro-compatible
- Nuvlabox status - Do not send the whole nuvlabox-status in a reply of an edit
- Nuvlabox status - Always set name descr acl at edit (#833)
  request made by a NuvlaBox (#825)
- Deployment - New logic to set execution-mode
- Deployment - Allow user to set execution-mode 
- Deployment - Refactor add and edit
- Deployment - Detach action response changed
- Deployment - Add support for pull mode for logs (#823)
- Global - Replace db/retrieve by crud/retrieve-by-admin as much as possible
- Fix reflective access warning and clj-kondo fixes
- Atom binding - workaround incoherence in binding es and atom
- Atom Binding - support scripted edit
- ES Binding - allow scripted edit and partial edit (#815)
- Binding - Remove non-used options
- Binding - allow scripted edit
- Deployment set - Accept :module key in PUT calls to replace app set
- Deployment set - Add fleet-filter property
- Deployment set - Add recompute-fleet action, to recompute the edges based on
  the fleet filter (#829)
- Make definition of std-crud/add-fn similar to std-crud/edit-fn (#824)
- Filter parser - Transform various enhancement and build balanced tree to avoid
  full stack error while parsing (#832)

## [6.1.0] - 2023-09-25

- Resources dynamic load - Make resource initialisation order predictable (#813)
- Std-crud - Pre-validate-hook for add-fn and edit-fn
- Std-edit - Call set-operations with updated resource to make operations coherent
- Job - Allow a job to be cancelled and propagate cancel to children jobs if it has children jobs
- Job - Introduction of multimethods `on-done` `on-timeout` `on-cancel` of jobs
- Job - Support `parent-job` to easily query children jobs
- Job - Do not allow edit in final state avoid
- Job - Remove stop action and related state
- Deps - `metosin/tilakone` introduction
- State machine - Reusable helper to drive state of resources by action and allow to place transition guards
- Deployment - On-cancel move deployment state to ERROR
- Deployment - Check if user has access to deployment set when resolving the name of it
- Deployment set - Complete refactor with a new state machine
- Deployment set - Cancel operation
- Deployment set - Operational status
- Deployment set - Update operations list based on the stored operational status
- Deployment set - Force delete operation
- Deployment set - Module version extraction fix
- Deployment set - On-done for update operational status fix
- Deployment set - Current deployments should contain only name and value bugfix
- Deployment set - Env vars comparison should only consider overwritten values

## [6.0.19] - 2023-08-22

- NuvlaEdge status - propagate name and description of NuvlaEdge when updated or
  created (#796)
- Deployment get-context - add nuvlaedge and nuvlaedge-status (#795)
- Module initialization - create apps-sets project
- Module utils - Helpers to get latest published apps or latest index
- Deployment set create helper to create module application set (#794)
- Deployment-set - plan action return target instead of credential

## [6.0.18] - 2023-07-21

- Deps - parent v6.7.14
- Module - Allow user with edit rights to change price but keep initial vendor
  account id
- Module - Add module price lifecycle tests
- Module - Create product for each price change
- Deployment spec - Deprecate subscription id
- Deployment - No more in charge of creating and deleting subscriptions
- Deployment - Remove upcoming invoice action
- Time - Helper end of date
- Module - Prevent user from deleting a module of subtype project that has
  children

## [6.0.17] - 2023-06-23

- Module - Restrict app creation by checking parent project acl, subtype and
  edit access
- Module - Refactor module resource
- Deployment set - State NEW introduction and change state machine accordingly
- Module applications sets - Support for container regirstries #772

## [6.0.16] - 2023-05-17

- Deployment - adds bulk editing tags endpoints #761
- Nuvlabox status - new attribute "cluster-node-labels"
- Module - Add vendor email to spec

## [6.0.15] - 2023-05-03

### Changed

- Deployment - Check credential only when edited but force resolve when check is
  done

## [6.0.14] - 2023-05-02

### Changed

- Deployment - Allow edit of deployment when credential is not changed

## [6.0.13] - 2023-04-28

### Changed

- Crud - Get resource helpers
- Deployment - tasklist#2352 bugfix and refactor
- Deployment - Execution-mode mixed by default at creation time tasklist#2348
- Module applications sets - Make each application set subtype explicit #766
- Notifications - async publication of messages from resources to Kafka

## [6.0.12] - 2023-04-24

### Changed

- Module - Let user set compatibility flag
- Module application - Deprecate unsupported-options
- ES binding - Support bulk-edit
- Spec helper - Generic spec validator
- Spec helper - Request body validator
- Common body - Spec for common request body specs
- Nuvlabox - Adds bulk editing endpoints for tags
- Module - Resolve vendor email
- Module - New subtype applications set
- Deployment set - Experimental v2

## [6.0.11] - 2023-02-22

### Changed

- Nuvlabox - Check credential should not be launched at each update of Nuvlabox

## [6.0.10] - 2023-02-21

### Changed

- Configuration - Typo fix in error-msg-not-authorised-redirect
- Nuvlabox release - Published flag support
- Spec - Validation report id of resource when existing
- Credential - Add Post add hook to create check credential jobs for swarm
- Infrastructure - Add swarm-manager flag
- Nuvlabox - Commission doesn't delete credential neither infrastructure
- Nuvlabox - Remove subtype from the description of infrastructure
- Nuvlabox - Remove Infra prefix from name of infrastructure at creation time

## [6.0.9] - 2023-01-06

### Changed

- Cloud-entry-point - Status should be 500 when ES is down
- Email content test - Make current date constant for tests
- Notifications - Updated to use Kafka 3.x client. Updated the schema of the
  configuration of subscriptions to notifications. Removed creation of
  individual subscriptions.

## [6.0.8] - 2022-12-19

### Changed

- Logging - fix logging of claims
- Deployment - support vendor outside platform's region nuvla/nuvla#110
- Module - Fix operations of resources in collection
- Dependencies - Update to parent 6.7.12
- Module - Follow customer trial not taken into account at creation time bug fix
- Deployment - Allow vendor and users with edit right on module with price to start it without subscription

## [6.0.7] - 2022-12-06

### Changed

- Nuvlabox status - denormalize nuvlabox-engine-version
- Nuvlabox status - refactor denormalization code
- Module - Add `delete-version` operation and keep delete call to delete all versions #724
- Module - Operations map should present specific versions urls when retrieving a specific version #724
- Deployment set - Experimental feature
- CI - concurrency and build on tag push

## [6.0.6] - 2022-11-17

### Changed

- NuvlaEdge - host level management (playbooks) bugfixes
- NuvlaEdge status - create the spec for the new "network" attribute

## [6.0.5] - 2022-10-14

### Changed

- 2FA - email token format should always be on 6 digits #714
- Payment - utils to extract tax rate from catalog and get-catalog helper
- Deployment - Subscription created with tax when applicable

## [6.0.4] - 2022-09-13

- Remove select keys from session configuration bugfix

## [6.0.3] - 2022-09-13

### Changed

- OIDC configurtation - Deprecate redirect-url-resource and public-key. Added
  jwks-url
- OIDC - Use instead jwks-url to search for
  corresponding public-key depending on kid #704
- OIDC - Deprecate callback redirect
- Nuvlabox resource log - shared acl users with view-acl only cannot delete
  resource log bugfix. Simplify resource log acl
- Nuvlabox status - next-heartbeat should not be changed when online is set to
  false by admin

## [6.0.2] - 2022-08-03

### Added

- Nuvla Config - Make email header image configurable

## [6.0.1] - 2022-07-22

### Added

- Routing - HTTP CORS preflight checks support

### Changed

- Email sending - Enhance logging when error occur

## [6.0.0] - 2022-06-29

### Added

- Group - Allow group to create subgroups
- Time - Helper truncate to days
- Subscription of root group is inherited in sub-group
- Session - New operation `get-groups` added. This operation allow user to get
  all groups hierarchies that he is part of

### Changed

- Pricing - Rename method `delete-discount-customer` to `delete-discount`
- Group - By default add connected user into created group members
- Session - Get peers get also users of subgroups
- Deployment - Fix interacting with non-free resources
- Group - Bulk delete removed not needed
- Session - Deprecate groups attribute in session and cookie
- Session - Operation `switch-group` allow user to switch to subgroups that he
  is implicitly part of

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
