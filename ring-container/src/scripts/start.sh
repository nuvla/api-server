#!/bin/sh -e

CP='/opt/nuvla/server/resources:/opt/nuvla/server/lib/*'

for i in `ls /opt/nuvla/server/lib-ext/*`
do
  CP="${CP}:${i}/*"
done

/usr/bin/java -cp $CP sixsq.nuvla.server.ring
