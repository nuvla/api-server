#!/bin/sh -e

/opt/nuvla/server/bin/generate-auth-keys.sh || true

/opt/nuvla/server/bin/start.sh
