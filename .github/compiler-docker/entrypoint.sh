#!/bin/bash
set -e

maxzoom=11
datapath=data-$RANDOM
cd frontend
printf "VITE_MAX_ZOOM=$maxzoom\nVITE_DATA_PATH=$datapath" > .env.local
npm ci
npm run build
cd ../tile-compiler
./compile.sh $maxzoom shortcut $datapath