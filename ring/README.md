
Nuvla Ring Server
=================

This module contains a simple, generic ring application container that
can be reused for the collection of micro-services that make up the
Nuvla platform.

Initialization and Finalization Functions
-----------------------------------------

To make use of this ring application container, the micro-service must
provide a single initialization function that:

 * Takes no arguments
 * Initializes the micro-service
 * Returns a two-element tuple containing the ring handler and an
   optional finalization function

All of the namespace loading is done dynamically, so the micro-service
does not need AOT compilation.

The optional finalization function will be called prior to shutting
down the micro-service.  It must:

 * Take no arguments
 * Release/close resources for the micro-service
 * Not block the shutdown process

Exceptions from the finalization script will be caught, logged, and
then ignored.

Starting with the REPL
----------------------

To start the service via the REPL, directly use the `start` function:

```
(require '[sixsq.nuvla.server.ring :as server])
(def stop (server/start 'sixsq.nuvla.server.example/init 5000))
;; service on  http://localhost:5000 should return "Ring Example Running!"
(stop)
```

This will load the namespace "sixsq.nuvla.server.ring-example" and
execute the initialization function "init" from that namespace.  It
will then start the service asynchronously on the port "5000".  The
function returns a shutdown function, which must be called to stop the
server. The boot and shell environment (excepting the classpath) will
not affect a server started in this way.

Starting with a Container
-------------------------

See the documentation in the `rpm-container` module. 

Logging
-------

The service assumes that `clojure.tools.logging` will be used with the
SLF4J and log4j implementation.  These are included in the
dependencies.  The test `log4j.properties` file will ignore all
logging.  Modify this if you want to see the logging while testing.

The containerized server will provide a `log4j.properties` file that
logs everything to the console (as is typical for containers).
