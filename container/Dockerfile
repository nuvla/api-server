FROM nuvla/ring:test
                
ADD target/dependency/lib /opt/nuvla/server/lib.d/api
ADD src/scripts/generate-auth-keys.sh /opt/nuvla/server/bin/generate-auth-keys.sh
ADD src/scripts/start-api.sh /opt/nuvla/server/bin/start-api.sh

RUN chmod 0755 /opt/nuvla/server/bin/generate-auth-keys.sh
RUN chmod 0755 /opt/nuvla/server/bin/start-api.sh

ENV NUVLA_SERVER_INIT=sixsq.nuvla.server.app.server/init
ENV PERSISTENT_DB_BINDING=sixsq.nuvla.db.es.loader
ENV EPHEMERAL_DB_BINDING=sixsq.nuvla.db.es.loader
ENV ES_HOST=localhost
ENV ES_PORT=9300

ENTRYPOINT ["/opt/nuvla/server/bin/start-api.sh"]
