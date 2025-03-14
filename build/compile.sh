#!/bin/bash
set -e

echo "I am '$(whoami)'"

echo "Downloading newest mapping data."
kart clone https://github.com/pf-wikis/mapping-data.git /w/data

echo "Building frontend"
datahash=`expr $(date +%s) / 60`
cd /w/frontend
printf "VITE_DATA_HASH=$datahash" > ./.env.local
npm ci
npm run build

echo "Compiling tiles"
cd /w/tile-compiler
mvn -B compile package
java -jar target/tile-compiler.jar compileTiles -maxZoom 12 -useBuildShortcut -prodDetail -mappingDataFile ../data/data.gpkg
cd /w

# copy results to output
echo "Copying results"
cp -rf frontend/dist/* /w/output/

# clean up old files
echo "Cleaning old files"
find /w/output -mtime +7 -type f -delete
