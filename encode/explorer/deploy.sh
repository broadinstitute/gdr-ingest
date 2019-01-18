#!/usr/bin/env bash

set -euo pipefail

declare -r SCRIPT_DIR=$(cd $(dirname $0) && pwd)
declare -r PROJECT_ROOT=$(dirname $(dirname ${SCRIPT_DIR}))
declare -r CONFIG_DIR=${SCRIPT_DIR}/config
declare -r DB_DIR=${SCRIPT_DIR}/db
declare -r UI_DIR=${SCRIPT_DIR}/ui
declare -r STAGING_DIR=${SCRIPT_DIR}/target/docker/stage
declare -r RENDERED_CONFIG_PATH=opt/docker/conf/explorer.conf

declare -rA VALID_ENVS=([dev]=1 [prod]=2)

declare -r CLOUDSQL_PROXY_VERSION=1.13

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

  local -rA env_map=(
    [INPUT_PATH]=${input_path}
    [OUT_PATH]=${output_path}
    [ENVIRONMENT]=${env}
    [CONFIG_PATH]=/${RENDERED_CONFIG_PATH}
  )

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

function start_proxy () {
  local -r env=$1 name=$2 tmp=$3

  2>&1 echo Launching Cloud SQL proxy for DB in ${env}...

  local -r service_account_json=${tmp}/service-account.json
  vault read -format=json secret/dsde/gdr/encode/${env}/cloudsql-proxy-key.json | jq .data > ${service_account_json}

  docker run -d --rm \
    --name=${name} \
    -v ${service_account_json}:/config \
    gcr.io/cloudsql-docker/gce-proxy:${CLOUDSQL_PROXY_VERSION} \
    /cloud_sql_proxy \
    -instances=$(vault read -field=db_instance_id secret/dsde/gdr/encode/${env}/explorer)=tcp:0.0.0.0:5432 \
    -credential_file=/config
}

function run_liquibase () {
  local -r env=$1 db_container=$2
  2>&1 echo Running liquibase migrations...

  local -r vault_path=secret/dsde/gdr/encode/${env}/explorer

  docker run --rm \
    --link ${db_container} \
    -v ${DB_DIR}/changelog.xml:/workspace/changelog.xml:ro \
    -v ${DB_DIR}/changesets:/workspace/changesets:ro \
    -e LIQUIBASE_HOST=${db_container} \
    -e LIQUIBASE_DATABASE=$(vault read -field=db_name ${vault_path}) \
    -e LIQUIBASE_USERNAME=$(vault read -field=db_username ${vault_path}) \
    -e LIQUIBASE_PASSWORD=$(vault read -field=db_password ${vault_path}) \
    -e LIQUIBASE_CHANGELOG=changelog.xml \
    kilna/liquibase-postgres \
    liquibase update
}

function deploy_appengine () {
  local -r env=$1
  local -r project=$(vault read -field=app_project secret/dsde/gdr/encode/${env}/explorer)

  # Push the frontend first because it's the "default" service, and in a fresh project Google
  # enforces the default get pushed before any other services.
  2>&1 echo Pushing frontend to App Engine in ${env}...
  gcloud --project=${project} app deploy --quiet ${UI_DIR}/app.yaml
  2>&1 echo Pushing backend to App Engine in ${env}...
  gcloud --project=${project} app deploy --quiet ${STAGING_DIR}/app.yaml
  2>&1 echo Setting up routing in ${env}...
  gcloud --project=${project} app deploy --quiet ${CONFIG_DIR}/dispatch.yaml
}

function main () {
  check_usage ${@}

  local -r env=$1
  2>&1 echo Deploying to ${env}...

  stage_docker
  render_ctmpl ${env}

  local -r proxy_name=proxy-$(cat /dev/urandom | LC_ALL=C tr -dc a-zA-Z0-9 | fold -w 32 | head -n 1)
  local -r proxy_tmp=$(mktemp -d ${STAGING_DIR}/cloudsql-proxy.XXXXXX)
  trap "2>&1 echo Stopping Cloud SQL proxy...; docker stop $proxy_name; rm -r $proxy_tmp" EXIT

  start_proxy ${env} ${proxy_name} ${proxy_tmp}
  run_liquibase ${env} ${proxy_name}
  deploy_appengine ${env}
}

main ${@}
