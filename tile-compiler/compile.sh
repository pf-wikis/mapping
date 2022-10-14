#!/bin/bash
set -e

maxZoom=7

targetDir="../frontend/public/data"
rm -rf $targetDir
rm -rf geo
mkdir geo
mkdir -p "$targetDir"

#compile sprites
spritezero "$targetDir/../sprites" "sprites/"
spritezero "$targetDir/../sprites@2x" "sprites/" --retina

toGeoJSON() {
	local target="$tmp/${1:8:-4}.json"
	rm -f $target
	ogr2ogr -f GeoJSON "$target" -t_srs EPSG:4326 "${1}" \
		-dim XY \
		-mapFieldType DateTime=String
}

compile() {
	local target="geo/${1:11}"
	local lTarget="geo/${1:11:-8}_label.geojson"
	local tmp="geo/tmp.geojson"
	cp $1 "$target"
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
		sed -i -E 's/("type":"Feature".*?),("tippecanoe":\{[^\}]+\})/\2,\1/' "$lTarget"
		rm -rf "$tmp"
	fi
}

#compile maps
for f in ../sources/*.geojson; do
	echo "" && echo ""
	echo "Transforming $f to GeoJSON"
	compile $f
done

# compile borders
mapshaper geo/country.geojson -clean -snap precision=0.0001 -innerlines -dissolve -o geo/borders.geojson geojson-type=FeatureCollection
rm -rf geo/country.geojson

# make tiles
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
	--no-feature-limit \
	--no-tile-size-limit \
	\
	$layers
