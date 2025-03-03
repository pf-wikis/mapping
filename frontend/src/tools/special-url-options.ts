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

        if(options.get('bbox')) {
            let pad = 0;//window.innerWidth*0.075;
            console.log(`bbox with padding ${pad}`);
            let bbox = options.get('bbox').split(',').map(Number.parseFloat) as LngLatBoundsLike;
            options.delete('bbox');
            window.location.hash = '#'+options.toString();
            map.fitBounds(bbox, {animate: false, padding: pad});
        }
    });
}