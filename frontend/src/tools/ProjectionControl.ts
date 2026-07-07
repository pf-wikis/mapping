import { GlobeControl } from "maplibre-gl";
import { GolarionMap } from "./GolarionMap";
import { projection as defaultProjection } from "../ml-style/projection";

export class ProjectionControl extends GlobeControl {
    #map: GolarionMap;

    constructor(map:GolarionMap) {
        super();
        this._map = map.map;
        this.#map = map;
        map.options.onChange('projection', (oldValue, newValue) => {
            this.switchTo(newValue=='globe'?'auto':newValue);
        });
    }

    switchTo(value:'auto'|'mercator') {
        this.#map.onStyleLoaded(()=>{
            this._map.setProjection(value=='mercator'?{type:'mercator'}:defaultProjection);
            this._updateGlobeIcon();
        });
    }

    _updateGlobeIcon = (): void => {
        this._globeButton.classList.remove('maplibregl-ctrl-globe');
        this._globeButton.classList.remove('maplibregl-ctrl-globe-enabled');
        if(this.#map.options.projection === 'mercator') {
            this._globeButton.classList.add('maplibregl-ctrl-globe');
            this._globeButton.title = this._map._getUIString('GlobeControl.Enable');
        }
        else {
            this._globeButton.classList.add('maplibregl-ctrl-globe-enabled');
            this._globeButton.title = this._map._getUIString('GlobeControl.Disable');
        } 
    };

    _toggleProjection = ():void => {
        const currentProjection = this._map.getProjection()?.type;
        if (currentProjection === 'mercator' || !currentProjection) {
            this.#map.options.projection = 'auto';
        } else {
             this.#map.options.projection = 'mercator';
        }
    };
}