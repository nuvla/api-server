#!/bin/sh -e

#
# The following environment variables should be set:
#
# NUVLA_SERVER_INIT: namespace of server's ring handler
# NUVLA_SERVER_PORT: port number
# NUVLA_SERVER_HOST: host name or address 
#

#
# Core resources and libraries appear first on classpath.
#

CP='/opt/nuvla/server/resources:/opt/nuvla/server/lib/*'

#
# Includes all directories in /opt/nuvla/server/lib.d in
# the classpath with wildcards.  All dependent containers
# should put there jar files there.
#
for i in `ls /opt/nuvla/server/lib.d/`
do
  CP="${CP}:/opt/nuvla/server/lib.d/${i}/*"
done

#
# Actually start the server. This will start in daemon
# mode and (normally) will not exit.
#
cmd="/usr/bin/java -cp ${CP} sixsq.nuvla.server.ring"
echo $cmd
$cmd

