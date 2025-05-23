
Nuvla Api Container
====================

This module contains the definition for the Nuvla Api container that
builds from the generic ring container.

When running the build of this repository, it will create a Docker
container called `nuvla/api`.

Configuration
-------------

The following environmental variables are provided by the parent
`nuvla/ring`: 

 - NUVLA_SERVER_INIT: provides the namespace that will create the ring
   handler for the service.  This is normally set in the Dockerfile.
 - NUVLA_SERVER_PORT: provides the port to use for the service.  The
   default has been reset to port 8201.
 - NUVLA_SERVER_HOST: defaults to 0.0.0.0 so that the service listens
   on all interfaces.
 - CLOJURE_TOOLS_LOGGING_TO_TELEMERE: Allow to redirect clojure tools logging to Telemere logging lib.
 - TAOENSSO_TELEMERE_RT_MIN_LEVEL: Configuration to change the log level and filters.

The following environental variable are used by the api server:

 - AUTH_PUBLIC_KEY: provides the path for the public key used for
   authentication.  The public/private key pair is usually generated;
   with the public key written to the default path.
 - ZK_ENDPOINTS: provides the endpoints for the Zookeeper service.
   Defaults to 'localhost:2181'.
 - PERSISTENT_DB_BINDING: provides the namespace for the database
   binding loader to use.  Defaults to the Elasticsearch binding:
   `sixsq.nuvla.db.es.loader`. 
 - EPHEMERAL_DB_BINDING: provides the namespace for the ephemeral
   database binding to use.  Defaults to the Elasticsearch binding:
   `sixsq.nuvla.db.es.loader`.
 - JSON_LOGGING: provides a boolean to enable json logging.

If you use the Elasticsearch binding, you will also may also need to
provide the following environmental variables as well:

 - ES_HOST: hostname or address for the Elasticsearch database.
   Defaults to 'localhost'.
 - ES_PORT: port for the Elasticsearch database.  Defaults to 9200.

To run this, do the following:

```sh
docker run -p 8201:8201 nuvla/api
```

setting any of the environment variables that you need to change.

**NOTE**: The database and Zookeeper services must be available when
starting the container.  You probably want to deploy the full system
with the provided docker-compose file.

The api service will be available on http://localhost:8201/api,
which should respond with the cloud entry point.
