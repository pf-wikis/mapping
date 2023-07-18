#!/bin/bash
set -e
mvn compile package
java \
 -jar target/tile-compiler.jar
