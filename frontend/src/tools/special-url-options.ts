import { CenterZoomBearing, Map, GeoJSONSource } from "maplibre-gl";
import { timeIndexEnd, timeIndexStart } from "../utils/BasicStyleFilters.js";
import { GolarionMap } from "./GolarionMap.js";

export function addSpecialURLOptions(map:GolarionMap) {
    map.onStyleLoaded(() => {
        map.options.onChange('highlight', (oldValue, newValue) => {
            if(newValue) {
                let url = `${HOST}/highlights/${newValue}.geojson?v=${BUILD_DATA_HASH}`;
                if(oldValue) {
                    (map.map.getSource('highlights') as GeoJSONSource)?.setData(url);
                }
                else {
                    map.map.addSource('highlights', {
                        type: 'geojson',
                        data: url
                    });
                    map.map.addLayer({
                        id: 'highlights',
                        type: 'fill',
                        source: 'highlights',
                        filter: ['all', timeIndexStart, timeIndexEnd],
                        paint: {
                        'fill-color': 'rgb(0, 0, 0)',
                        'fill-opacity': 0.3,
                        }
                    },'location-icons');
                }
            }
            else {
                map.map.removeLayer('highlights');
                map.map.removeSource('highlights');
            }
        });

        if(map.startupOptions.flyTo) {
            //test with http://localhost:5173/#location=7.14/41.918/-9.832&flyTo=7.81/31.433/-0.639
            console.log('Fly to ', map.startupOptions.flyTo);
            let flyTo = map.startupOptions.flyTo.split('/');
            if(flyTo.length != 3) {
                console.log('Invalid flyTo parameter', map.startupOptions.flyTo);
                return;
            }
            map.map.flyTo({
                center: [Number(flyTo[2]), Number(flyTo[1])],
                zoom: Number(flyTo[0]),
                speed: .8 
            });
        }

        if(map.startupOptions.bbox) {
            let bbox = map.startupOptions.bbox.split(',').map(Number.parseFloat) as number[];
            let zoom = map.startupOptions.zoom??7;
            let cam:CenterZoomBearing|undefined;
            if(bbox.length==4) {
                cam = map.map.cameraForBounds(bbox as [number, number, number, number]);
            }
            else if(bbox.length==2) {
                cam = {center:bbox as [number, number], zoom:zoom};
            }
            console.log(`bbox with ${bbox} to `, cam);
            if(!cam)
                cam = {center:[0,0], zoom:zoom}
            map.map.jumpTo(cam);
        }
    });
}
