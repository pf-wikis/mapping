#!/bin/bash
set -e

export MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:9999 -Dcom.sun.management.jmxremote.port=9998 -Dcom.sun.management.jmxremote.ssl=false -Dcom.sun.management.jmxremote.authenticate=false"
mvn compile exec:java