#!/usr/bin/env bash

KEYS_LOC=/etc/slipstream/auth
PRIVKEY=$KEYS_LOC/auth_privkey.pem
PUBKEY=$KEYS_LOC/auth_pubkey.pem
user=slipstream
group=$user

if [ ! -f $PRIVKEY ]; then
   rm -f $PRIVKEY $PUBKEY
   mkdir -p $KEYS_LOC
   chown $user.$group $KEYS_LOC
   chmod 700 $KEYS_LOC
   openssl genrsa -out $PRIVKEY 2048
   openssl rsa -pubout -in $PRIVKEY -out $PUBKEY
   chown $user.$group $PRIVKEY $PUBKEY
   chmod 400 $PRIVKEY $PRIVKEY
fi