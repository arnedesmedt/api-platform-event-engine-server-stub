#!/usr/bin/env bash
docker run --rm -v "${PWD}:/local" openapitools/openapi-generator-cli generate \
    -i /local/resources/api-spec.json \
    -g php-symfony \
    -o /local/output