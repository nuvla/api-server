# Deploying CIMI with Docker

This document describes how you can run the CIMI server using Docker
containers.

## Prerequisites

Ensure that [Docker](https://www.docker.com/) and [Docker
Compose](https://docs.docker.com/compose/) installed.

Make sure that you've allocated enough memory to Docker. If you're
using Elasticsearch for persistent storage, you will need 4-6 GB of
memory allocated to Docker..

## Prepare Directory

Create the directory structure you find in the
[directory](https://github.com/slipstream/SlipStreamServer/tree/master/cimi-deployment)
hosting this README file.  Copy the files you also find there.

If you've cloned this repository, you can use the `cimi-deployment`
directory in your workspace.

## Certificate Configuration

The CIMI server must be accessible over HTTPS. For this, use the
[Traefik](https://traefik.io/) reverse proxy that can use self-signed
certificates (for testing) or can manage [Let's
Encrypt](https://letsencrypt.org/) certificates (for production).

You must decide if you will be using self-signed certificates or
certificates provided by Let's Encrypt.

For **self-signed certificates**, copy
`components/traefik/cert/traefik-self-signed.toml` to
`components/traefik/cert/traefik.toml`. Then generate a certificate:

```
$ (cd components/traefik/cert && openssl req  -nodes -new -x509  -keyout server.pem -out server.crt)
```

You'll need to provide values for some of the requested fields.

For **Let's Encrypt certificates**, copy
`components/traefik/cert/traefik-lets-encrypt.toml` to
`components/traefik/cert/traefik.toml`.  Then replace all of the
*$variable$* patterns with the correct values.  You must have an email
address and a fully-qualified domain name (FQDN).

## Starting Service

You will find a `docker-compose.yml` file at the root of this
directory.  This will manage the complete deployment including the
CIMI servers and underlying database.

If you wish, you can modify the contents of this file to customize the
deployment.  For example,
[Kibana](https://www.elastic.co/products/kibana) is optional.

The version of the server is taken from the environment.  You must
define:

```sh
$ export SLIPSTREAM_VERSION=3.48-SNAPSHOT
```

replacing the version number with the one you want to use.

To start everything, run `docker-compose up` from the root directory.

## Testing Service

After waiting some time for everything to start, you should be able to
see the CloudEntryPoint at the address
[https://localhost/api/cloud-entry-point](https://localhost/api/cloud-entry-point),
replacing *localhost* if you want to test access over the Internet.

You'll get the usual browser warning about the security of the
certificates, if you used the self-signed certificates for the server.

## Terminate Service

To terminate everything, `ctrl+c` and run `docker-compose down -v` from the root
directory.
