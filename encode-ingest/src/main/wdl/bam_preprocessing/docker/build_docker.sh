#!/usr/bin/env bash

declare -r SCRIPT_DIR=$(cd $(dirname $0) && pwd)

declare -r IMAGE_VERSION="1-SNAPSHOT"
declare -r IMAGE_TAG="us.gcr.io/broad-gdr-encode/picard-alpine:$IMAGE_VERSION"

docker build -t ${IMAGE_TAG} ${SCRIPT_DIR}
docker push ${IMAGE_TAG}
