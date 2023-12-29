import { LngLat, Map } from "maplibre-gl";

export function addSpecialURLOptions(map: Map) {
    if(!window.location.hash) return;
    let params = new URLSearchParams(window.location.hash.substring(1));


    map.on('load', function () {
        if(params.has('flyTo')) {
            //test with http://localhost:5173/#location=7.14/41.918/-9.832&flyTo=7.81/31.433/-0.639
            console.log('Fly to');
            let flyTo = params.get('flyTo').split('/');
            params.delete('flyTo');
            window.location.hash = '#'+params.toString();
            map.flyTo({
                center: [Number(flyTo[2]), Number(flyTo[1])],
                zoom: Number(flyTo[0]),
                speed: .8 
            });
        }
    });
}