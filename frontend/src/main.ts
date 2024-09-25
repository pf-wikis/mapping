import Maplibre, { AttributionControl, Map, NavigationControl, ScaleControl } from "maplibre-gl";
import 'maplibre-gl/dist/maplibre-gl.css';

import layers from './layers.js';
import MeasureControl from './tools/measure.js';
import './style.scss';
import { PMTiles, Protocol } from 'pmtiles';
import { makeLocationsClickable } from "./tools/location-popup.js";
import { addRightClickMenu } from "./tools/right-click-menu.js";
import { addSpecialURLOptions } from "./tools/special-url-options.js";
import { CachedSource } from "./CachedPmTiles.js";

//check if running embedded
var urlParams = new URLSearchParams(window.location.hash.replace("#","?"));
const embedded = (urlParams.get('embedded') === 'true');
const mapContainer = document.getElementById("map-container");

if(embedded) {
  mapContainer.classList.add("embedded");
}

var root = `${location.protocol}//${location.host}/`;

let pmtilesProt = new Protocol();
Maplibre.addProtocol("pmtiles", pmtilesProt.tilev4);
//add custom tile caching
if(indexedDB) {
  try {
    pmtilesProt.add(new PMTiles(new CachedSource(root+'golarion.pmtiles?v='+import.meta.env.VITE_DATA_HASH)))
  } catch(e) {
    console.log("Failed to initialize IndexDB cache")
    console.log(e)
  }
}

export const map = new Map({
  container: 'map-container',
  hash: 'location',
  attributionControl: false,
  pitchWithRotate: false,
  style: {
    version: 8,
    sources: {
      golarion: {
        type: 'vector',
        attribution: '<a href="https://paizo.com/licenses/communityuse">Paizo CUP</a>, <a href="https://github.com/pf-wikis/mapping#acknowledgments">Acknowledgments</a>',
        url: 'pmtiles://'+root+'golarion.pmtiles?v='+import.meta.env.VITE_DATA_HASH
      }
    },
    sprite: root+'sprites/sprites',
    layers: layers,
    glyphs: root+'fonts/{fontstack}/{range}.pbf',
    transition: {
      duration: 300,
      delay: 0
    }
  },
});
//diable rotation
map.dragRotate.disable();
map.touchZoomRotate.disableRotation();

map.on('error', function(err) {
  console.log(err.error.message);
});
if(!embedded) {
  map.addControl(new NavigationControl({showCompass: false}));
}
map.addControl(new ScaleControl({
  unit: 'imperial',
  maxWidth: embedded?50:100,
}));
map.addControl(new ScaleControl({
  unit: 'metric',
  maxWidth: embedded?50:100,
}));
map.addControl(new AttributionControl({
  compact: true
}));
let measureControl = new MeasureControl();
map.addControl(measureControl);

makeLocationsClickable(map);
addRightClickMenu(embedded, map, measureControl);
addSpecialURLOptions(map);


//////////debugging options
//map.showCollisionBoxes = true;
