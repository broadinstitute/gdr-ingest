#!/usr/bin/env bash
set -euo pipefail

# versions of tools used
declare -r IMAGES_VERSION=1.0
declare -r SAMTOOLS_VERSION=1.9

function build_docker () {
  # using the tool/dir name
  local -r image_tag=$1
  # get the version of the tool/docker
  local -r image_version=$2
  # get the relative path to the docker
  local -r dockerfile_path="docker/${image_tag}/Dockerfile"
  # get the images name
  local -r image_name="us.gcr.io/broad-gdr-encode/${image_tag}:${image_version}"
  # get additional arguments with the "--build-arg" option
  local -a build_args=()
  for arg in $@; do
    build_args=(--build-arg ${arg} ${build_args[@]})
  done

  # build and push the docker
  docker build ${build_args[@]} -t ${image_name} -f ${dockerfile_path} .
  docker push ${image_name}
}

# build dockers (dir/tool name must be 1st arg & version name must be 2nd arg)
build_docker samtools-with-gsutil ${IMAGES_VERSION} SAMTOOLS_VERSION=${SAMTOOLS_VERSION}
build_docker samtools-with-parallel ${IMAGES_VERSION} SAMTOOLS_VERSION=${SAMTOOLS_VERSION}
