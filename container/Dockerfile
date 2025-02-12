FROM nuvla/ring:2.2.0
                
ARG GIT_BRANCH
ARG GIT_COMMIT_ID
ARG GIT_DIRTY
ARG GIT_BUILD_TIME
ARG TRAVIS_BUILD_NUMBER
ARG TRAVIS_BUILD_WEB_URL
ARG USER_GROUP=nuvla

LABEL git.branch=${GIT_BRANCH}
LABEL git.commit.id=${GIT_COMMIT_ID}
LABEL git.dirty=${GIT_DIRTY}
LABEL git.build.time=${GIT_BUILD_TIME}
LABEL ci.build.number=${TRAVIS_BUILD_NUMBER}
LABEL ci.build.web.url=${TRAVIS_BUILD_WEB_URL}

COPY target/dependency/lib /opt/nuvla/server/lib.d/api
COPY src/scripts/session-key-pair.sh /opt/nuvla/server/bin/
COPY src/scripts/start-api.sh /opt/nuvla/server/bin/

ENV NUVLA_SERVER_INIT=com.sixsq.nuvla.server.app.server/init
ENV PERSISTENT_DB_BINDING=com.sixsq.nuvla.db.es.loader
ENV EPHEMERAL_DB_BINDING=com.sixsq.nuvla.db.es.loader
ENV ES_ENDPOINTS=localhost:9200
ENV JSON_LOGGING=false

RUN addgroup -S "$USER_GROUP"  \
    && adduser -S "$USER_GROUP" -G "$USER_GROUP" \
    && mkdir -p /etc/nuvla \
    && chown "$USER_GROUP:$USER_GROUP" /etc/nuvla \
    && chmod 0755 /opt/nuvla/server/bin/session-key-pair.sh \
    && chmod 0755 /opt/nuvla/server/bin/start-api.sh

USER "$USER_GROUP"
ENTRYPOINT ["/opt/nuvla/server/bin/start-api.sh"]
