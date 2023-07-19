#!/bin/bash
set -e

cd ./tile-compiler
mvn -B compile package
java -jar target/tile-compiler.jar clean