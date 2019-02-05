
Nuvla CIMI Container
====================

This module contains the definition for the Nuvla CIMI container that
builds from the generic ring container.

When running the build of this repository, it will create a Docker
container called `nuvla/cimi-container`.

Running
-------

To run this, do the following:

```sh
docker run -p 8201:8201 nuvla/cimi-container
```

**NOTE**: Elasticsearch and Zookeeper services must be available when
using the default database connector.

The CIMI service will be available on http://localhost:8201/api,
which should respond with the cloud entry point. 
