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

echo "Using Bandit version: $VERSION"

# Extract documentation directly from Python source files
# This replaces the HTML-based extraction approach
python3 scripts/extract_docs_from_source.py "$BASE_DIR" "docs" "$VERSION"

echo "Documentation generation complete!"
