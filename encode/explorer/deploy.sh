#!/usr/bin/env bash

set -euo pipefail

declare -r SCRIPT_DIR=$(cd $(dirname $0) && pwd)
declare -r PROJECT_ROOT=$(dirname $(dirname ${SCRIPT_DIR}))
declare -r CONFIG_DIR=${SCRIPT_DIR}/config
declare -r UI_DIR=${SCRIPT_DIR}/ui
declare -r STAGING_DIR=${SCRIPT_DIR}/target/docker/stage
declare -r RENDERED_CONFIG_PATH=opt/docker/conf/explorer.conf

declare -rA VALID_ENVS=([dev]=1 [prod]=2)

function check_usage () {
  if [[ $# -ne 1 ]]; then
    2>&1 echo Error: Incorrect number of arguments given, expected 1 '(environment)' but got $#
    exit 1
  elif [[ -z "${VALID_ENVS[$1]-}" ]]; then
    2>&1 echo Error: Invalid environment "'$1'", valid values are: ${!VALID_ENVS[@]}
    exit 1
  fi
}

function stage_docker () {
  2>&1 echo "Staging Docker image..."
  cd ${PROJECT_ROOT} && \
    sbt 'encode-explorer/docker:stage' &&
    cd ~-
}

function render_ctmpl () {
  local -r env=$1

  2>&1 echo "Rendering configuration..."
  local -r input_path=/working/config/in
  local -r output_path=/working/config/out

  local -A env_map
  env_map[INPUT_PATH]=${input_path}
  env_map[OUT_PATH]=${output_path}
  env_map[ENVIRONMENT]=${env}
  env_map[CONFIG_PATH]=/${RENDERED_CONFIG_PATH}

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
  local -r project=$(vault read -field=app_project secret/dsde/gdr/encode/${env}/explorer)

  2>&1 echo "Pushing frontend to App Engine in ${env}..."
  gcloud --project=${project} app deploy --quiet ${UI_DIR}/app.yaml
  2>&1 echo "Pushing backend to App Engine in ${env}..."
  gcloud --project=${project} app deploy --quiet ${STAGING_DIR}/app.yaml
  2>&1 echo "Setting up routing in ${env}..."
  gcloud --project=${project} app deploy --quiet ${CONFIG_DIR}/dispatch.yaml
}

function main () {
  check_usage ${@}

  local -r env=$1
  2>&1 echo Deploying to ${env}...

  stage_docker
  render_ctmpl ${env}
  deploy_appengine ${env}
}

main ${@}
