# Changelog

## [Unreleased]

  - Add a new user template to allow user to invite another person to use nuvla

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

 
