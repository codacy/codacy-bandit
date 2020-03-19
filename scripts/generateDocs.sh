#!/usr/bin/env bash

set -e

VERSION=$(cat requirements.txt | while read line; do
  version=$(echo $line | sed "s/^bandit==\(.*\)$/\1/")
  if [ $version == $line ]; then
    VERSION=$version
  else
    echo $version
  fi
done)

BASE_DIR="bandit"

docker run -v $(pwd):/src -w /src python bash scripts/generatePythonDocs.sh $VERSION $BASE_DIR

sbt "doc-generator/run $VERSION $(pwd)/$BASE_DIR"
