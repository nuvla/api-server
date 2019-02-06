Minimal Nuvla Deployment
========================

This directory contains a Docker compose file that describes a minimal
Nuvla deployment containing Elasticsearch, Zookeeper, and the CIMI
server.

Starting
--------

This can be started with the command:

```sh
docker-compose up -d
```

You can view the CIMI server logs by running the command:

```sh
docker-compose logs -f cimi
```

The logs will indicate when the CIMI server on port 8201 is
available.

Bootstrapping
-------------

The database will be empty, with no users or any other resources. To
bootstrap the server by creating a super user, use the following
procedure.

Create a hashed password value for the super user:

```sh
echo -n "plaintext-password" | \
  sha512sum | \
  cut -d ' ' -f 1 | \
  tr '[:lower:]' '[:upper:]'
```

Create a file named `user-template-super.json` that contains the
following:

```json
{
    "userTemplate" : {
        "href" : "user-template/direct",
        "username" : "super",
        "password" : "${hashed_password}",
        "emailAddress" : "super@example.com",
        "state" : "ACTIVE",
        "isSuperUser" : true
    }
}
```

replacing `${hashed_password}` with the value you generated above.

Create the super user via the CIMI server:

```sh
curl -XPOST \
     -H 'slipstream-authn-info:internal ADMIN' \
     -H 'content-type:application/json' \
     -d@user-template-super.json \
     http://localhost:8201/api/user
```

You will now be able to log into server as the `super` user:

```sh
curl -XPOST \
     -d href=session-template/internal \
     -d username=super \
     -d password=${plaintext_password} \
     http://localhost:8201/api/session
```

replacing `${plaintext_password}` with the original plaintext value of
your password.

You can then configure your server normally via the API.

Stopping
--------

To stop the server, simply do the following:

```sh
docker-compose down -v
```

This should stop the containers and remove the containers and any
volumes that were created.
