#!/bin/bash
set -e

echo "I am '$(whoami)'"

echo "Downloading newest mapping data."
if [ -f data/data.gpkg ]; then
   cd data && kart pull && cd ..
else
   kart clone https://github.com/pf-wikis/mapping-data.git data
fi

echo "Building frontend"
datahash=$RANDOM
cd frontend
printf "VITE_DATA_HASH=$datahash" > ./.env.local
npm ci
npm run build

echo "Compiling tiles"
cd ../tile-compiler
mvn -B compile package
java -jar target/tile-compiler.jar compileTiles -maxZoom 13 -useBuildShortcut -dataHash $datahash -prodDetail -mappingDataFile ../data/data.gpkg
cd ..

# copy results to output
echo "Copying results"
cp -rf frontend/dist/* /w/output/

# clean up old files
echo "Cleaning old files"
find /w/output -mtime +7 -type f -delete
