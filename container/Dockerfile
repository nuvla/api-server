FROM nuvla/ring:2.0.3
                
ARG GIT_BRANCH
ARG GIT_COMMIT_ID
ARG GIT_DIRTY
ARG GIT_BUILD_TIME
ARG TRAVIS_BUILD_NUMBER
ARG TRAVIS_BUILD_WEB_URL

LABEL git.branch=${GIT_BRANCH}
LABEL git.commit.id=${GIT_COMMIT_ID}
LABEL git.dirty=${GIT_DIRTY}
LABEL git.build.time=${GIT_BUILD_TIME}
LABEL travis.build.number=${TRAVIS_BUILD_NUMBER}
LABEL travis.build.web.url=${TRAVIS_BUILD_WEB_URL}

ADD target/dependency/lib /opt/nuvla/server/lib.d/api

ADD src/scripts/start-api.sh /opt/nuvla/server/bin/start-api.sh
RUN chmod 0755 /opt/nuvla/server/bin/start-api.sh
ADD src/scripts/session-key-pair.sh /opt/nuvla/server/bin/session-key-pair.sh
RUN chmod 0755 /opt/nuvla/server/bin/session-key-pair.sh

ENV NUVLA_SERVER_INIT=sixsq.nuvla.server.app.server/init
ENV PERSISTENT_DB_BINDING=sixsq.nuvla.db.es.loader
ENV EPHEMERAL_DB_BINDING=sixsq.nuvla.db.es.loader
ENV ES_ENDPOINTS=localhost:9200

ENTRYPOINT ["/opt/nuvla/server/bin/start-api.sh"]
