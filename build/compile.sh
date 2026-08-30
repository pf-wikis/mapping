#!/bin/bash
set -e

echo "I am '$(whoami)'"
qgis_process -v

echo "Downloading newest mapping data."
kart clone https://github.com/pf-wikis/mapping-data.git /w/data

echo "Compiling tiles"
cd /w/tile-compiler
mvn -B compile package
java -jar target/tile-compiler.jar compileTiles -maxZoom 15 -prodDetail -mappingDataFile ../data/data.gpkg
cd /w

echo "Building frontend"
datahash=`expr $(date +%s) / 60`
cd /w/frontend
npm ci
npm run build
cd /w

# copy results to output
echo "Copying results"
cp -rf frontend/dist/* /w/output/

# clean up old files
echo "Cleaning old files"
find /w/output -mtime +14 -type f -delete
find /w/output -type d -empty -delete

# link latest
cd /w/output
HASH="$(cat ./latest.json)"
ln -sfn "./$HASH" ./latest
