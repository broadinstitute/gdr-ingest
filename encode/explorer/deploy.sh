#!/usr/bin/env bash

set -euo pipefail

declare -r SCRIPT_DIR=$(cd $(dirname $0) && pwd)
declare -r PROJECT_ROOT=$(dirname $(dirname ${SCRIPT_DIR}))
declare -r CONFIG_DIR=${SCRIPT_DIR}/config
declare -r STAGING_DIR=${SCRIPT_DIR}/target/docker/stage
declare -r UI_DIR=${SCRIPT_DIR}/ui
declare -r RENDERED_CONFIG_PATH=opt/docker/conf/explorer.conf

declare -r DB_PROJECT=broad-gdr-encode-storage
declare -r DB_REGION=us-central1
declare -r DB_INSTANCE=encode-metadata
declare -r DB_NAME=postgres

declare -r DEPLOY_PROJECT=broad-gdr-encode

function stage_docker () {
  2>&1 echo "Staging Docker image..."
  cd ${PROJECT_ROOT} && \
    sbt 'encode-explorer/docker:stage' &&
    cd ~-
}

function render_ctmpl () {
  2>&1 echo "Rendering configuration..."
  local -r input_path=/working/config/in
  local -r output_path=/working/config/out

  local -A env_map
  env_map[INPUT_PATH]=${input_path}
  env_map[OUT_PATH]=${output_path}
  env_map[ENVIRONMENT]=there-is-only-one-env-but-render-templates-requires-one
  env_map[CONFIG_PATH]=/${RENDERED_CONFIG_PATH}
  env_map[DB_NAME]=${DB_NAME}
  env_map[POSTGRES_INSTANCE]=${DB_PROJECT}:${DB_REGION}:${DB_INSTANCE}
  env_map[DEPLOY_PROJECT]=${DEPLOY_PROJECT}

  local -r ctmpl_env_file=${CONFIG_DIR}/env-vars.txt
  for key in ${!env_map[@]}; do
    echo ${key}=${env_map[$key]} >> ${ctmpl_env_file}
  done

  docker run --rm \
    -v ${HOME}/.vault-token:/root/.vault-token \
    -v ${CONFIG_DIR}:${input_path} \
    -v ${STAGING_DIR}:${output_path} \
    --env-file=${ctmpl_env_file} \
    broadinstitute/dsde-toolbox \
    /usr/local/bin/render-templates.sh

  rm ${ctmpl_env_file}
  mkdir -p ${STAGING_DIR}/$(dirname ${RENDERED_CONFIG_PATH})
  mv ${STAGING_DIR}/$(basename ${RENDERED_CONFIG_PATH}) ${STAGING_DIR}/${RENDERED_CONFIG_PATH}
}

function deploy_appengine () {
  2>&1 echo "Pushing to App Engine..."
  gcloud --project=${DEPLOY_PROJECT} app deploy --quiet ${STAGING_DIR}/app.yaml ${UI_DIR}/app.yaml
  2>&1 echo "Setting up routing..."
  gcloud --project=${DEPLOY_PROJECT} app deploy --quiet ${CONFIG_DIR}/dispatch.yaml
}

function main () {
  stage_docker
  render_ctmpl
  deploy_appengine
}

main
