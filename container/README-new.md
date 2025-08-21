## Title & Description
- Nuvla api-server rest api service.

## Docker Image Information
- Image name and versioning strategy:
  - `nuvla/api:6.15.0`
  - Uses semantic versioning.

## Usage
- Basic `docker run` command with required environment variables, ports, and volumes:
  ```sh
  docker run -d \
    --name nuvla-api \
    --network backend \
    --network frontend \
    --publish 8200:8200 \
    --restart on-failure \
    --env ES_ENDPOINTS=es \
    --env ZK_ENDPOINTS=zk:2181 \
    --env NUVLA_SUPER_PASSWORD=supeR8-supeR8 \
    --env KAFKA_PRODUCER_INIT=yes \
    --env KAFKA_ENDPOINTS=kafka:9092 \
    nuvla/api:6.15.0
  ```

## Configuration
| Variable      | Description                                                                        | Default            |
|--------------|------------------------------------------------------------------------------------|--------------------|
| `NUVLA_SERVER_PORT`    | Provides the port to use for the service.                                          | `8200`             |
| `NUVLA_SERVER_HOST`    | Ip address of interface to listens on.                                             | `0.0.0.0`          |
| `ES_ENDPOINTS`    | Elasticsearch endpoint(s) comma separated list of host1[:port][,host2[:port],...]. | `localhost:9200`   |
| `ES_SNIFFER_INIT`   | Boolean to enable Elasticsearch hosts using a sniffer.                             | `false`            |
| `ES_SNIFF_INTERVAL`   | Sniffing interval in milliseconds.                                                 |                    |
| `ES_SNIFF_AFTER_FAILURE_DELAY`   | Boolean to enable Elasticsearch hosts using a sniffer.                             |                    |
| `ZK_ENDPOINTS`   | Provides the endpoints for the Zookeeper service.                                  | `localhost:2181`   |
| `NUVLA_SUPER_PASSWORD`   | When defined, on service startup a super user is created with provided password.   |                    |
| `KAFKA_PRODUCER_INIT`   | Initialise async communication channel with kafka.                                 | `false`            |
| `KAFKA_ENDPOINTS`   | Kafka endpoint used mainly for notifications.                                      | `localhost:9092`   |
| `CLOJURE_TOOLS_LOGGING_TO_TELEMERE`   | Allow to redirect clojure tools logging to Telemere logging lib.                   | `true`             |
| `TAOENSSO_TELEMERE_RT_MIN_LEVEL`   | Configuration to change the log level and filters.                                 | `{:default :info}` |
| `KAFKA_ENDPOINTS`   | Kafka endpoint used mainly for notifications.                                      | `localhost:9092`   |
| `JSON_LOGGING`   | Provides a boolean to enable json logging.                                         | `false`            |

## Network Configuration
### Exposed Ports
- List of ports the service listens on.
  - **8201**: Service endpoint

## Volumes & Persistent Storage
- Required/optional mount points for persistent data.
- When configured, RSA certificate for session token signing
  - session.crt
  - session.key

## Dependencies
- Required services:
   - Elasticsearch
   - Zookeeper
- Optional services:
  - Kafka

## Health Checks
- How to check if the service is running & ready:
  - `wget -qO- http://localhost:8200/api/cloud-entry-point || exit 1`

## Security Considerations
- The container runs as:
  - non-root
- Requested elevation:
  - N/A
- TLS/SSL is delegated to third party components (e.g. Traefik)

## Logging & Debugging
- Log levels, locations, and debugging options:
   - Logs go to stdout/stderr

## Hardware Requirements
- Minimum CPU/RAM requested for the service.
  - 1 vCPU / 500MB RAM

## Stateless
- `Yes`

## Affinity with Other Containers
- Whether this service should be scheduled on specific nodes alongside other services:
   - Affinity with other containers is not required.

## Scaling & High Availability
- Whether the container can be replicated (multi-instantiated):
   - Supports any number of concurrently run instances.

## Startup & Dependency Order
- Whether this container requires another service to be up first:
   - Elasticsearch is required, but if not available, the container exits. COE should restart the container.
   - Zookeeper is required, but if not available, the container exits. COE should restart the container.
   - When configured, Kafka is required, but if not available, the container exits. COE should restart the container.

## API Reference
[Full Rest API documentation](https://docs.nuvla.io/nuvla/user-guide/api/)
- Main API endpoints with a brief description.
  - `GET /api/cloud-entry-point` – Retrieve list of available resources
  - `PUT /api/user` – Search for existing user
  - `PUT /api/session` – Search available user session
  - `PUT /api/nuvlabox` – Search available NuvlaEdges
  - `PUT /api/module?subtype=application` – Search available applications

## Example Deployment Configurations
  Docker swarm `docker-compose.yml`:
  ```yaml

networks:
   frontend:
      driver: "overlay"
      name: "frontend"
   backend:
      driver: "overlay"
      name: "backend"
      
secrets:
   session.crt:
      file: ./secrets/session.crt
   session.key:
      file: ./secrets/session.key

services:
  api:
    image: nuvla/api:6.15.0
    environment:
       - ES_ENDPOINTS=es
       - ES_SNIFFER_INIT=yes
       - ES_SNIFF_INTERVAL=5001
       - ES_SNIFF_AFTER_FAILURE_DELAY=1001
       - ZK_ENDPOINTS=zk:2181
       - NUVLA_SUPER_PASSWORD=supeR8-supeR8
       - KAFKA_PRODUCER_INIT=yes
       - KAFKA_ENDPOINTS=kafka:9092
    secrets:
       - source: session.crt
         target: /etc/nuvla/session/session.crt
       - source: session.key
         target: /etc/nuvla/session/session.key
    deploy:
       restart_policy:
          condition: on-failure
          delay: 5s
    networks:
       - backend
       - frontend
    healthcheck:
       start_period: 30s
       retries: 5
       test: wget -qO- http://localhost:8200/api/cloud-entry-point || exit 1
  ```

## License & Support
- License: Apache 2.0
