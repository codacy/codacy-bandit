#!/usr/bin/env bash

set -e

VERSION=$1
BASE_DIR=$2

rm -rf "$BASE_DIR"

git clone -b "$VERSION" --single-branch --depth 1 "https://github.com/PyCQA/bandit.git" $BASE_DIR
pip3 install virtualenv
virtualenv "./$BASE_DIR/venv"
./"$BASE_DIR"/venv/bin/pip3 install -U -r "$BASE_DIR/requirements.txt"
./"$BASE_DIR"/venv/bin/pip3 install -r "$BASE_DIR/doc/requirements.txt"
./"$BASE_DIR"/venv/bin/sphinx-build "$BASE_DIR/doc/source/" "$BASE_DIR/doc/build/" -b html -a -D html_add_permalinks=
