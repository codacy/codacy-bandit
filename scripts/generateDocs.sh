#!/usr/bin/env bash

set -e

VERSION="$(cat bandit-version | tr -d '\n')"
BASE_DIR="bandit"

rm -rf "$BASE_DIR"
git clone -b "$VERSION" --single-branch --depth 1 "https://github.com/PyCQA/bandit.git" bandit
virtualenv "./$BASE_DIR/venv"
./"$BASE_DIR"/venv/bin/pip install -U -r "$BASE_DIR/requirements.txt"
./"$BASE_DIR"/venv/bin/pip install -r "$BASE_DIR/doc/requirements.txt"
./"$BASE_DIR"/venv/bin/sphinx-build "$BASE_DIR/doc/source/" "$BASE_DIR/doc/build/" -b html -a -D html_add_permalinks=
sbt "doc-generator/run $VERSION $(pwd)/$BASE_DIR"