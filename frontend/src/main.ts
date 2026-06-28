import { addProtocol, AttributionControl, FilterSpecification, GlobeControl, Map, NavigationControl, ProjectionDefinitionSpecification, PropertyValueSpecification, ScaleControl, setWorkerUrl, StyleSpecification } from "maplibre-gl";
import 'maplibre-gl/dist/maplibre-gl.css';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import './style.scss';

import style from 'virtual:style';
import MeasureControl from './tools/measure.js';

import { PMTiles, Protocol } from 'pmtiles';
import { makeLocationsClickable } from "./tools/location-popup.js";
import { addRightClickMenu } from "./tools/right-click-menu.js";
import { addSpecialURLOptions } from "./tools/special-url-options.js";
import { CachedSource } from "./CachedPmTiles.js";
import NewTab from "./tools/NewTab.js";
import { CompactAttributionControl } from "./tools/CompactAttributionControl.js";
import { GolarionMap } from "./tools/GolarionMap.js";
import SearchControl from "./tools/SearchControl.js";
import TimeSliderControl from "./tools/TimeSliderControl.js";
import options from "./URLOptions.js";
import timeMeta from "./utils/timeMeta.js";

//check if running embedded
const mapContainer = document.getElementById("map-container")!;

if(!options.embedded) {
  mapContainer.classList.remove("embedded");
}

var root = `${location.protocol}//${location.host}`;

let pmtilesProt = new Protocol();
//add custom tile caching
if(indexedDB) {
  try {
    //if this url does not match the one in style we do not cache
    pmtilesProt.add(new PMTiles(new CachedSource(root+'/golarion.pmtiles?v='+import.meta.env.BUILD_DATA_HASH)))
  } catch(e) {
    console.log("Failed to initialize IndexDB cache")
    console.log(e)
  }
}
addProtocol("pmtiles", pmtilesProt.tilev4);
setWorkerUrl(workerUrl);

/******************************* update style according to option *******************************/

if(options.hideLabels) {
  style.layers = style.layers.filter(l=>!l.id.includes('label'));
}
if(options.hideLocations) {
  style.layers = style.layers.filter(l=>!l.id.includes('location'));
}
if(options.hideBorders) {
  style.layers = style.layers.filter(l=>!l.id.includes('border'));
}

console.log("Effective style", style);

/************************* end of style adjustments ****************************************/


export const map = new Map({
  container: 'map-container',
  hash: 'location',
  attributionControl: false,
  pitchWithRotate: options.embedded?false:true,
  style: style,
  pixelRatio: Math.max(window.devicePixelRatio || 1, 2),
  canvasContextAttributes: {
    preserveDrawingBuffer: true
  }
});
export const golarionMap = new GolarionMap();
golarionMap.map = map;
//project to globe
let projection:PropertyValueSpecification<ProjectionDefinitionSpecification>;
if(options.projection === 'auto') {
  projection = [
    "interpolate",
    ["linear"],
    ["zoom"],
    4,
    "vertical-perspective",
    5,
    "mercator"
  ];
}
else {
  projection = options.projection;
}
map.once('style.load', () => {
  map.setProjection({
    type: projection
  });
});

//diable rotation
map.dragRotate.disable();
map.touchZoomRotate.disableRotation();

map.on('error', function(err) {
  console.log(err.error.message);
});
let timeSlider = new TimeSliderControl(golarionMap, options.year?timeMeta.fromYear(options.year):0);

if(!options.embedded) {
  map.addControl(new GlobeControl());
  map.addControl(new NavigationControl({showCompass: true}));
  map.addControl(timeSlider, 'top-left');
  map.addControl(new SearchControl(golarionMap), 'top-left');
}
map.addControl(new ScaleControl({
  unit: 'imperial',
  maxWidth: options.embedded?50:100,
}));
map.addControl(new ScaleControl({
  unit: 'metric',
  maxWidth: options.embedded?50:100,
}));
map.addControl(new CompactAttributionControl(options.embedded));
let measureControl = new MeasureControl(golarionMap);
map.addControl(measureControl);
if(options.embedded) {
  map.addControl(new NewTab());
  //attribution._toggleAttribution();
  //map.once('load', e=>attribution._toggleAttribution());
}

makeLocationsClickable(golarionMap);
addRightClickMenu(options.embedded, map, measureControl);
addSpecialURLOptions(map);

//change label orientation if bearing != 0
function changeStyleWithBearing() {
  map.setGlobalStateProperty('rotated', map.getBearing() !== 0);
}
map.on('rotateend', changeStyleWithBearing);
map.on('style.load', changeStyleWithBearing);


//////////debugging options
//map.showTileBoundaries = true;
//map.showCollisionBoxes = true;
if (import.meta.env.MODE === 'development') {
  (window as any).map = map;
  (window as any).MAP_VERSION = BUILD_DATA_HASH;
}
