#!/bin/bash
set -e

cd ./tile-compiler
mvn -B compile exec:java -Dexec.args="clean"