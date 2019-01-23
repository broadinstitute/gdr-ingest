#!/usr/bin/env bash

set -euo pipefail

declare -r SCRIPT_DIR=$(cd $(dirname $0) && pwd)
declare -r UI_DIR=${SCRIPT_DIR}/ui

# What we really want is to have a set of valid environment tokens
# with a "contains" check.
# As far as I can tell that isn't supported in bash.
# Instead we use the valid env strings as map keys with non-empty values.
# To check if a token is a valid env, we index into the map and assert
# that the result is non-empty.
declare -rA VALID_ENVS=([dev]=valid [prod]=valid)

function check_usage () {
  if [[ $# -ne 1 ]]; then
    2>&1 echo Error: Incorrect number of arguments given, expected 1 '(environment)' but got $#
    exit 1
  elif [[ -z "${VALID_ENVS[$1]-}" ]]; then
    2>&1 echo Error: Invalid environment "'$1'", valid values are: ${!VALID_ENVS[@]}
    exit 1
  fi
}

function deploy_appengine () {
  local -r env=$1
  local -r project=$(vault read -field=app_project secret/dsde/gdr/encode/${env}/explorer)

  2>&1 echo Pushing frontend to App Engine in ${env}...
  gcloud --project=${project} app deploy --quiet ${UI_DIR}/app.yaml
}

function main () {
  check_usage ${@}

  local -r env=$1

  deploy_appengine ${env}
}

main ${@}
