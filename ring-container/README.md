
Nuvla Ring Server Container
===========================

This module contains a simple, generic ring application container that
can be reused for the collection of micro-services that make up the
Nuvla platform.

When running the build of this repository, it will create a Docker
container called `nuvla/ring-container`, which contains an example
ring handler.

Running Example
---------------

To run this, do the following:

```sh
docker run -p 5000:5000 nuvla/ring-container
```

The example service will be available on
http://localhost:5000/example, which should respond with the message
"Ring Example Running!".

Running Other Ring Handlers
---------------------------

To use a different ring handler, configure the port, or configure the
host, see the README in the ring module.
