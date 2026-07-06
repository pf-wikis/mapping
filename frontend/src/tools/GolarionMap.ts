import { Map, MapStyleLoadEvent } from "maplibre-gl";
import { TimeSlice } from "../../gen/timeMeta";
import { StateProp, StateTypes } from "../ml-style/state";
import { init as initUrlOptions, Options, startupOptions } from "../URLOptions";

export class GolarionMap {
    map: Map;
    startupOptions = startupOptions;
    options: Options;
    mode: 'view'|'draw-wait'|'draw-draw' = 'view';
    styleLoaded: Promise<MapStyleLoadEvent & Object>;

    constructor(map:Map) {
        this.map = map;
        this.options = initUrlOptions(this);
        this.styleLoaded = map.once('style.load');
    }

    setState<K extends StateProp>(key:K, value:StateTypes[K]) {
        this.onStyleLoaded(() => {
            this.map.setGlobalStateProperty(key, value);
        });
    }

    setStateYear(time:TimeSlice) {
        this.setState('timeIndex', time.id);
    }

    onStyleLoaded(callback:()=>void) {
        this.styleLoaded.then(callback);
       
        /*
        if (this.map.isStyleLoaded()) {
            callback();
            return;
        }

        //guarantees there is not racing condition
        const run = () => {
            this.map.off("load", run);
            this.map.off("style.load", run);
            callback();
        };
        this.map.once("style.load", run);
        this.map.once("load", run);
        if (this.map.isStyleLoaded()) {
            run();
        }*/
    }
}