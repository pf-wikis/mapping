import { LngLat, LngLatBoundsLike, Map } from "maplibre-gl";

export function addSpecialURLOptions(map: Map) {
    if(!window.location.hash) return;
    let options = new URLSearchParams(window.location.hash.substring(1));


    map.on('load', function () {
        if(options.has('flyTo')) {
            //test with http://localhost:5173/#location=7.14/41.918/-9.832&flyTo=7.81/31.433/-0.639
            console.log('Fly to');
            let flyTo = options.get('flyTo').split('/');
            options.delete('flyTo');
            window.location.hash = '#'+options.toString();
            map.flyTo({
                center: [Number(flyTo[2]), Number(flyTo[1])],
                zoom: Number(flyTo[0]),
                speed: .8 
            });
        }
    });
    
    if(options.get('bbox')) {
        console.log(`bbox with padding ${pad}`);
        let bbox = options.get('bbox').split(',').map(Number.parseFloat) as float[];
        options.delete('bbox');
        window.location.hash = '#'+options.toString();
        let cam:CenterZoomBearing;
        if(bbox.length==4) {
            cam = map.cameraForBounds(bbox);
        }
        else if(bbox.length==2) {
            cam = {center:bbox, zoom:7};
        }
        else {
            cam = {center:[0,0], zoom:7}
        }
        map.jumpTo(cam);
    }
}