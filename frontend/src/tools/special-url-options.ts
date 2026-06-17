import { CenterZoomBearing, Map } from "maplibre-gl";
import options from "../URLOptions.js";

export function addSpecialURLOptions(map: Map) {
    map.once('style.load', function () {
        if(options.highlight) {
          map.setGlobalStateProperty('highlighted', options.highlight);
        }
    });
    map.once('load', function () {
        if(options.flyTo) {
            //test with http://localhost:5173/#location=7.14/41.918/-9.832&flyTo=7.81/31.433/-0.639
            console.log('Fly to');
            let flyTo = options.flyTo.split('/');
            options.flyTo = undefined;
            options.writeToHash();
            map.flyTo({
                center: [Number(flyTo[2]), Number(flyTo[1])],
                zoom: Number(flyTo[0]),
                speed: .8 
            });
        }
    });
    
    if(options.bbox) {
        let bbox = options.bbox.split(',').map(Number.parseFloat) as number[];
        let zoom = options.zoom??7;
        console.log(`bbox with ${bbox}`);
        options.bbox = undefined;
        options.writeToHash();
        map.once('load', function () {
            let cam:CenterZoomBearing|undefined;
            if(bbox.length==4) {
                cam = map.cameraForBounds(bbox as [number, number, number, number]);
            }
            else if(bbox.length==2) {
                cam = {center:bbox as [number, number], zoom:zoom};
            }
            if(!cam)
                cam = {center:[0,0], zoom:zoom}
            map.jumpTo(cam);
        });
    }
}