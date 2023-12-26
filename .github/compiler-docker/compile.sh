#!/bin/bash
set -e

kart clone https://github.com/pf-wikis/mapping-data.git mapping-data
maxzoom=12
datahash=$RANDOM
cd frontend
printf "VITE_DATA_HASH=$datahash" > .env.local
npm ci
npm run build
cd ../tile-compiler
mvn -B compile package
java -jar target/tile-compiler.jar compileTiles -maxZoom $maxzoom -useBuildShortcut -dataHash $datahash -prodDetail -mappingDataFile ../mapping-data/mapping-data.gpkg