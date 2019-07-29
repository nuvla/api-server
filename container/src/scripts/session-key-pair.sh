#!/bin/sh -e

#
# Generate an RSA key pair for session token signing.
#
# In production, clustered environments, the session key and
# certificate should be created before running the server(s) and
# shared between nodes. Doing so ensures that the session tokens will
# work across the entire cluster.
#

#
# Determine the paths to the RSA key pair.
#

if [ -z "$NUVLA_SESSION_CRT" ]; then
    crt_path=/etc/nuvla/session/session.crt
else
    crt_path=${NUVLA_SESSION_CRT}
fi

if [ -z "$NUVLA_SESSION_KEY" ]; then
    key_path=/etc/nuvla/session/session.key
else
    key_path=${NUVLA_SESSION_KEY}
fi

if [ ! -f ${crt_path} -o ! -f ${key_path} ]; then

    #
    # generate session certificate and key when missing
    #
    
    mkdir -p `dirname ${crt_path}`
    mkdir -p `dirname ${key_path}`

    rm -f ${key_path} ${crt_path}

    openssl genrsa -out ${key_path} 2048
    openssl rsa -pubout -in ${key_path} -out ${crt_path}

    echo "using GENERATED session certificate: ${crt_path}"
    echo "using GENERATED session key: ${key_path}"
else
    echo "using EXISTING session certificate: ${crt_path}"
    echo "using EXISTING session key: ${key_path}"
fi
