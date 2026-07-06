import { Hash } from 'maplibre-gl';
import timeMeta, { TimeSlice } from '../gen/timeMeta';
import throttle from './utils/throttle';
import { GolarionMap } from './tools/GolarionMap';
import { HighlightLabel, highlightLabels } from '../gen/highlight_labels';
import style from 'virtual:style';

function parseOptions(): URLSearchParams {
    return window.location.hash?new URLSearchParams(window.location.hash.substring(1)):new URLSearchParams();
}
const projectionOptions = ['auto', 'globe', 'mercator'] as const;
type ProjectOption = typeof projectionOptions[number];


let initialRaw = parseOptions();
let zoom = initialRaw.get('zoom');
let projection = initialRaw.get('projection')??'auto';
export const startupOptions = {
    flyTo: initialRaw.get('flyTo')??undefined,
    bbox: initialRaw.get('bbox')??undefined,
    zoom: zoom?Number.parseFloat(zoom):undefined,
    embedded: initialRaw.get('embedded') === 'true'
}

const defaultOptions = {
    hideLabels: !style.state.showLabels.default,
    hideLocations: !style.state.showLocations.default,
    hideBorders: !style.state.showBorders.default,
    highlight: undefined as HighlightLabel|undefined,
    projection: 'auto' as ProjectOption,
    year: timeMeta.latest.start as number,
};
type OptionsData = typeof defaultOptions;
type OptionProp = keyof OptionsData;
type OptionsListener<K extends OptionProp> = (oldValue:OptionsData[K],newValue:OptionsData[K])=>void;

class OptionsHolder implements OptionsData {
    hideLabels!: boolean;
    hideLocations!: boolean;
    hideBorders!: boolean;
    highlight!: HighlightLabel|undefined;
    projection!: ProjectOption;
    year!: number;
    private _listeners:Map<OptionProp, Array<OptionsListener<any>>> = new Map();
    
    constructor() {
        Object.assign(this, defaultOptions);
    }

    public onChange<K extends OptionProp>(prop:K, listener:OptionsListener<K>) {
        this._listeners.set(prop, [...this._listeners.get(prop)??[], listener])
        if(this[prop] !== defaultOptions[prop]) {
            this._informListener(listener, prop, defaultOptions[prop], this[prop]);
            
        }
    }

    private _informListener<K extends OptionProp>(listener: OptionsListener<K>, key:K, oldValue: OptionsData[K], newValue: OptionsData[K]) {
        console.log(`Changed options for ${key}: ${oldValue} -> ${newValue}`);
        listener(oldValue, newValue);
    }

    _set<K extends OptionProp>(p:K, newValue:this[K]):boolean {
        let old = this[p];
        this[p] = newValue;
        if(old !== newValue) {
            for(let l of this._listeners.get(p)??[]) {
                this._informListener(l, p, old, newValue);
            }
            return true;
        }
        return false;
    }
};
export interface Options extends OptionsHolder {}


export function init(map:GolarionMap):Options {
    
    let year = initialRaw.get('year');
    
    const options = new Proxy(new OptionsHolder(), {
        set<K extends OptionProp>(target:OptionsHolder, p:K, newValue:OptionsHolder[K], receiver:any) {
            if(target._set(p, newValue)) {
                map.map._hash._updateHash();
            }
            return true;
        },
    });

    function updateOptions() {
        let raw = parseOptions();
        let highlight = raw.get('highlight')??'';
        options.hideLabels = raw.get('hideLabels') === 'true';
        options.hideLocations = raw.get('hideLocations') === 'true';
        options.hideBorders = raw.get('hideBorders') === 'true';
        options.highlight = (highlightLabels as readonly string[]).includes(highlight)?highlight as HighlightLabel:undefined;
        options.year = year?Number.parseInt(year)??timeMeta.latest.start:timeMeta.latest.start;
        options.projection = (projectionOptions as readonly string[]).includes(projection) ? projection as ProjectOption : 'auto';
    }
    updateOptions();

    options.onChange('hideLabels', (_,newValue) => map.setState('showLabels', !newValue));
    options.onChange('hideLocations', (_,newValue) => map.setState('showLocations', !newValue));
    options.onChange('hideBorders', (_,newValue) => map.setState('showBorders', !newValue));

    const originalGetHashString = Hash.prototype.getHashString;
    Hash.prototype.getHashString = function(mapFeedback?: boolean):string {
        let hash = Reflect.apply(originalGetHashString, this, [mapFeedback]);
        let params = new URLSearchParams(hash.substring(1));

        for(let k of Object.keys(defaultOptions) as (keyof OptionsData)[]) {
            if(options[k] === defaultOptions[k]) {
                params.delete(k);
            }
            else {
                params.set(k, options[k]?.toString() ?? '');
            }
        }
        for(let k of Object.keys(startupOptions)) {
            params.delete(k);
        }
        return `#${decodeURIComponent(params.toString())}`;
    }

    function onHashChange() {
        updateOptions();
        map.map._hash._onHashChange();
    }
    removeEventListener('hashchange', map.map._hash._onHashChange);
    addEventListener('hashchange', onHashChange);

    return options;
}