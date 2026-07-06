import { Map } from "maplibre-gl";
import { TimeSlice } from "../../gen/timeMeta";
import { StateProp, StateTypes } from "../ml-style/style";
import { init as initUrlOptions, Options, startupOptions } from "../URLOptions";

export class GolarionMap {
    map: Map;
    startupOptions = startupOptions;
    options: Options;
    mode: 'view'|'draw-wait'|'draw-draw' = 'view';

    constructor(map:Map) {
        this.map = map;
        this.options = initUrlOptions(this);
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
        if(this.map.isStyleLoaded()) {
            callback();
        }
        else {
            this.map.once('style.load', () => {
                callback();
            });
        }
    }
}