# Changelog

## [Unreleased]

  - Update the default user resource ACL to allow all user to see metadata
  - Related to the above, do not rewrite the ACL in the post-add for user resources
  - Fix a regex problem with dates that have only one digit
  - Add the id field to full text searches
  - Add a default value for :name for user resources if not provided explicitly (defaults to username then email)
  - Allow users to search the group collection and provide the view-meta rights for all users

## [2.0.0] - 2019-04-29

Initial, functionally-complete release. 

### Changed
  - Use java time instead of joda time
  - Update ring container to version 2.0.0

## [0.9.0] - 2019-04-17

Test release to verify release process.

### Changed

  - Update parent to version 6.3.0.

 
