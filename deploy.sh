#!/bin/bash

SERVER=$1
VERSION=$2

lein with-profile master uberjar
echo "Deploying to "${SERVER}
scp target/hackersome-${VERSION}-standalone.jar ${SERVER}:~
