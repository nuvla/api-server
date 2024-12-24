# Migration scripts

## How to run the scripts

The scripts code for the Nuvla migration scripts is contained in the namespaces `com/sixsq/nuvla/migration/script/*`.

### Setup

The following env vars should be defined to be able to run the scripts in the corresponding environment:
- Prod: `prod-username`, `prod-password`
- Preprod: `preprod-username`, `preprod-password`
- Alessandro dev machine: `dev-alb-username`, `dev-alb-password`
- Localhost: `local-username`, `local-password`

### Running

The scripts can be run via the REPL by adding the `:migration` profile. Typically each script
has a comment section at the end with the commands to be executed to run the script.
