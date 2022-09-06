#!/bin/bash
set -e

argv0=$(echo "$0" | sed -e 's,\\,/,g')
generatordir=$(dirname "$(readlink "$0" || echo "$argv0")")

use_winpty=0

case "$(uname -s)" in
  *CYGWIN*) generatordir=$(cygpath -w "$basedir");;
  MSYS*|MINGW*) use_winpty=1;;
  Linux) generatordir=$(dirname "$(readlink -f "$0" || echo "$argv0")");;
esac

generatordir=$(cd $(dirname $argv0) > /dev/null && cd $generatordir > /dev/null && pwd)
generatorimage=elastic/elastic-openapi-codegen-php
rootdir=`cd $(dirname $argv0)/../../../../../; pwd`

cd "${generatordir}" && docker build --target runner -t ${generatorimage} ee-api-php-server

if [ -x "${rootdir}/resources/scripts/before_run.sh" ]
then
  "${rootdir}"/resources/scripts/before_run.sh
fi

docker run --rm -v "${rootdir}":/local ${generatorimage} generate -g ee-api-php-server \
                                                               -i /local/resources/api/api-spec.yml \
                                                               -o /local/ \
                                                               -c /local/resources/api/config.json \
                                                               -t /local/resources/api/templates

cd "${rootdir}" && sudo chown -R "$(id -u):$(id -g)" Client.php ClientMock.php Model Endpoint

if [ -x "${rootdir}/resources/scripts/after_run.sh" ]
then
  "${rootdir}"/resources/scripts/after_run.sh
fi

# Exit code of phpcbf is 1 if all is fixed https://github.com/squizlabs/PHP_CodeSniffer/issues/2898
cd "${rootdir}" && (vendor/bin/phpcbf --extensions=php --report=full ./Client.php ./ClientMock.php ./ClientBuilder.php Model/ Endpoint/ || true)
