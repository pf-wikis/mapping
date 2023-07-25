#!/bin/bash
set -e
#set -o xtrace

for new in head/sources/*.gpkg; do
    old="${new:5}"
    if ! cmp -s "$old" "$new"; then
        echo $new changed
        oldJ="$old.geojson"
        newJ="$new.geojson"
        ogr2ogr -mapFieldType DateTime=String "$oldJ" "$old" -dim XY
        ogr2ogr -mapFieldType DateTime=String "$newJ" "$new" -dim XY


        if grep -q "\"type\": \"LineString\"" "$oldJ" || grep -q "\"type\": \"MultiLineString\"" "$oldJ" ; then
            mv "$oldJ" "$oldJ.unbuf.geojson"
            mv "$newJ" "$newJ.unbuf.geojson"
            qgis_process run native:buffer --distance_units=meters --area_units=m2 --ellipsoid=EPSG:7030 "--INPUT=$oldJ.unbuf.geojson" --DISTANCE='expression:"width" *0.000005' --SEGMENTS=5 --END_CAP_STYLE=0 --JOIN_STYLE=0 --MITER_LIMIT=2 --DISSOLVE=false "--OUTPUT='$oldJ'"
            qgis_process run native:buffer --distance_units=meters --area_units=m2 --ellipsoid=EPSG:7030 "--INPUT=$newJ.unbuf.geojson" --DISTANCE='expression:"width" *0.000005' --SEGMENTS=5 --END_CAP_STYLE=0 --JOIN_STYLE=0 --MITER_LIMIT=2 --DISSOLVE=false "--OUTPUT='$newJ'"
        fi


        


        mapshaper -i "$newJ" -erase "$oldJ" -o "added.geojson"
        mapshaper -i "$oldJ" -erase "$newJ" -o "removed.geojson"
        mapshaper -i "$newJ" -clip "$oldJ" -o "same.geojson"

        #calculate the bounds of the changes
        xmin=$(mapshaper -i "added.geojson" "removed.geojson" combine-files -merge-layers -calc "min(this.bounds?this.bounds[0]:null)" 2>&1 | grep -o -E "\-?[0-9]+\.?[0-9]*$")
        ymin=$(mapshaper -i "added.geojson" "removed.geojson" combine-files -merge-layers -calc "min(this.bounds?this.bounds[1]:null)" 2>&1 | grep -o -E "\-?[0-9]+\.?[0-9]*$")
        xmax=$(mapshaper -i "added.geojson" "removed.geojson" combine-files -merge-layers -calc "max(this.bounds?this.bounds[2]:null)" 2>&1 | grep -o -E "\-?[0-9]+\.?[0-9]*$")
        ymax=$(mapshaper -i "added.geojson" "removed.geojson" combine-files -merge-layers -calc "max(this.bounds?this.bounds[3]:null)" 2>&1 | grep -o -E "\-?[0-9]+\.?[0-9]*$")
        echo "Change bounds are [$xmin,$ymin,$xmax,$ymax]"

        xmin2=$( bc<<<"1.25*$xmin - 0.25*$xmax" )
        ymin2=$( bc<<<"1.25*$ymin - 0.25*$ymax" )
        xmax2=$( bc<<<"1.25*$xmax - 0.25*$xmin" )
        ymax2=$( bc<<<"1.25*$ymax - 0.25*$ymin" )
        echo "Clip bounds are   [$xmin2,$ymin2,$xmax2,$ymax2]"

        mapshaper -i "added.geojson" "removed.geojson" "same.geojson" combine-files \
            -style fill='#888' \
            -style fill='#8f8' where='this.layer.name=="added"' \
            -style fill='#f88' where='this.layer.name=="removed"' \
            -merge-layers \
            -clip bbox=$xmin2,$ymin2,$xmax2,$ymax2 \
            -o "results/${new:13:-5}.svg" width=800

        rm -rf "added.geojson"
        rm -rf "removed.geojson"
        rm -rf "same.geojson"

        printf "Changes in ${new:13:-5}:\n![${new:13:-5}](https://raw.githubusercontent.com/pf-wikis/mapping/diffstore/{{ pr }}/${new:13:-5}.svg)\n\n" \
        >> "comment.md"
    fi
done
