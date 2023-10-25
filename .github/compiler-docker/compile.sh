#!/bin/bash
set -e

maxzoom=13
datapath=data-$RANDOM
cd frontend
printf "VITE_DATA_PATH=$datapath" > .env.local
npm ci
npm run build
cd ../tile-compiler
mvn -B compile package
java -jar target/tile-compiler.jar compileTiles -maxZoom $maxzoom -useBuildShortcut -dataPath $datapath -prodDetail