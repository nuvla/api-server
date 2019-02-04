# Introduction to auth

## Create private and public keys

To create new private and public keys.  NB! The public key is not protected by
a password.

    $ cd test-resources
    $ rm auth_p*
    $ openssl genrsa -out auth_privkey.pem 2048
    $ openssl rsa -pubout -in auth_privkey.pem -out auth_pubkey.pem

## Build and install as a tiny library

This is used by SlipStream main server to check token validity locally
(no need to make a HTTP call).

    $ lein with-profile uberjar install

This will install `auth*.jar` into your local maven repository.

## Service

The authentication functions are included in the ssclj service.  Start
and test the authentication from there.
