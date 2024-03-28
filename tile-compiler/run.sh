#!/bin/bash
set -e
mvn compile package
java \
 -XX:ActiveProcessorCount=1 \
 -jar target/tile-compiler.jar
