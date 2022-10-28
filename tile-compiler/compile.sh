#!/bin/bash
set -e

maxZoom=${1:-7}
shortcut=${2}
dataPath=${3:-"data"}
prodDetail=${4:-"false"}

targetRoot="../frontend/public"
if [ "$shortcut" = "shortcut" ]; then
	targetRoot="../frontend/dist"
fi



targetDir="$targetRoot/$dataPath"
spriteDir="$targetRoot/sprites"
rm -rf $targetRoot/data*
rm -rf geo
mkdir geo
mkdir -p "$targetDir"
mkdir -p "$spriteDir"

#compile sprites
spritezero "$spriteDir/sprites" "sprites/"
spritezero "$spriteDir/sprites@2x" "sprites/" --retina

compile() {
	local target="${1}"
	local lTarget="${target:0:-8}_label.geojson"
	local tmp="geo/tmp.geojson"
	#if possible write geometry centers
	if grep -q -E "type\": *\"(Multi)?Polygon" "$target" && grep -q -E "Name\": *\"" "$target"; then
		echo "Generating centers"
		mapshaper "$target" \
			-filter 'Name != null' \
			-dissolve 'Name' \
			-each 'smallestEdge=Math.round(Math.min(this.width, this.height))' \
			-each 'tippecanoe={"minzoom" : Math.min('$maxZoom',Math.round(20/(Math.min(this.width, this.height)**2))) }' \
			-o "$tmp" geojson-type=FeatureCollection
		geojson-polygon-labels --precision=0.00001 --style=largest "$tmp" > "$lTarget"
		rm -rf "$tmp"
	fi
}

#compile maps
for f in ../sources/*.geojson; do
	echo "" && echo ""
	target="geo/${f:11}"
	echo "Transforming $f to GeoJSON $target"
	cp $f $target
	compile $target
done
for f in ../sources/*/*.shp; do
	echo "" && echo ""
	target="geo/$(basename $f .shp).geojson"
	echo "Transforming $f to GeoJSON $target"
	ogr2ogr "$target" "$f" \
		-dim XY \
		-mapFieldType DateTime=String
	compile $target
done

# compile borders
mapshaper geo/countries.geojson -clean -snap precision=0.0001 -innerlines -dissolve -o geo/borders.geojson geojson-type=FeatureCollection
rm -rf geo/countries.geojson

# remove borders from districts
mv geo/districts.geojson geo/tmp0.geojson
mapshaper geo/tmp0.geojson -clean -snap precision=0.0001 -innerlines -dissolve -o geo/tmp1.geojson geojson-type=FeatureCollection
qgis_process run native:buffer --distance_units=meters --area_units=m2 --ellipsoid=EPSG:7030 --INPUT=geo/tmp1.geojson --DISTANCE=0.0004 --SEGMENTS=5 --END_CAP_STYLE=0 --JOIN_STYLE=0 --MITER_LIMIT=2 --DISSOLVE=true --OUTPUT=geo/tmp2.geojson
qgis_process run native:difference --distance_units=meters --area_units=m2 --ellipsoid=EPSG:7030 --INPUT=geo/tmp0.geojson --OVERLAY=geo/tmp2.geojson --OUTPUT=geo/districts.geojson

# add detailing
maxDistance="500"
if [ "$prodDetail" = "true" ]; then
	maxDistance="250"
fi
mvn -B -f fractal-detailer compile exec:java -Dexec.args="$maxDistance\
   geo/chasms.geojson\
   geo/continents.geojson\
   geo/deserts.geojson\
   geo/forests.geojson\
   geo/hills.geojson\
   geo/ice.geojson\
   geo/mountains.geojson\
   geo/swamps.geojson\
   geo/waters.geojson"

# smooth rivers
mv 'geo/rivers.geojson' 'geo/tmp.geojson'
qgis_process run native:smoothgeometry --distance_units=meters --area_units=m2 --ellipsoid=EPSG:7030 --ITERATIONS=3 --OFFSET=0.25 --MAX_ANGLE=180 --INPUT='geo/tmp.geojson' --OUTPUT='geo/rivers.geojson'

# add minzoom to some layers
mapshaper "geo/districts.geojson" -each 'tippecanoe={"minzoom":4}' -o "geo/districts.geojson" force geojson-type=FeatureCollection
mapshaper "geo/districts_label.geojson" -each 'tippecanoe={"minzoom":4}' -o "geo/districts_label.geojson" force geojson-type=FeatureCollection
mapshaper "geo/rivers.geojson" -each 'if(width<5000){tippecanoe={"minzoom":1};}if(width<500){tippecanoe={"minzoom":4};}if(width<50){tippecanoe={"minzoom":7};}' -o "geo/rivers.geojson" force geojson-type=FeatureCollection

# move tippecanoe property up a level
for f in geo/*.geojson; do
	jq  -c '.features[] |= ((.tippecanoe = .properties.tippecanoe) | del(.properties.tippecanoe))' $f > $f.tmp && mv $f.tmp $f
done

# make tiles
rm -rf geo/tmp*.geojson
layers=""
for f in geo/*.geojson; do
	name="${f:4:-8}"
	echo "Adding layer $name"
	layers="$layers -L $name:$f"
done

tippecanoe -z${maxZoom} --no-tile-compression -n "golarion" -e "$targetDir/golarion" \
	--force \
	--detect-shared-borders \
	--coalesce \
	\
	-B 0 \
	--coalesce-densest-as-needed \
	--maximum-tile-bytes=200000 \
	--maximum-tile-features=100000 \
	\
	$layers


SECONDS=0
# rename extensions for github pages gzipping
echo "Changing pbf extensions"
for zoom in $targetDir/golarion/*/; do
	echo "  $zoom"
	mmv -r "$zoom*/*.pbf" "#2.pbf.json"
done
echo "$SECONDS"


#give maxZoom to vite
echo "Writing .env.local to $targetRoot/../.env.local"
printf "VITE_MAX_ZOOM=$maxZoom\nVITE_DATA_PATH=$dataPath" > "$targetRoot/../.env.local"