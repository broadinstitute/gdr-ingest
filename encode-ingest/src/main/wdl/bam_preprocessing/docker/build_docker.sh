#!/usr/bin/env bash

declare -r SCRIPT_DIR=$(cd $(dirname $0) && pwd)

declare -r PICARD_VERSION=2.18.14
declare -r IMAGE_TAG=us.gcr.io/broad-gdr-encode/picard-alpine:${PICARD_VERSION}

docker build -t ${IMAGE_TAG} --build-arg PICARD_VERSION=${PICARD_VERSION} ${SCRIPT_DIR}
docker push ${IMAGE_TAG}
