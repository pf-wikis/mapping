#!/bin/bash
set -e
mvn compile package
java\
 -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:9999 \
 -jar target/tile-compiler.jar
