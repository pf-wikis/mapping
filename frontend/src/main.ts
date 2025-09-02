import Maplibre, { AttributionControl, Map, NavigationControl, ScaleControl, StyleSpecification } from "maplibre-gl";
import 'maplibre-gl/dist/maplibre-gl.css';

import style from 'virtual:style';
import MeasureControl from './tools/measure.js';
import './style.scss';
import { PMTiles, Protocol } from 'pmtiles';
import { makeLocationsClickable } from "./tools/location-popup.js";
import { addRightClickMenu } from "./tools/right-click-menu.js";
import { addSpecialURLOptions } from "./tools/special-url-options.js";
import { CachedSource } from "./CachedPmTiles.js";
import NewTab from "./tools/NewTab.js";
import { CompactAttributionControl } from "./tools/CompactAttributionControl.js";
import { GolarionMap } from "./tools/GolarionMap.js";

//check if running embedded
var options = new URLSearchParams(window.location.hash.replace("#","?"));
const embedded = (options.get('embedded') === 'true');
const mapContainer = document.getElementById("map-container");

if(!embedded) {
  mapContainer.classList.remove("embedded");
}

var root = `${location.protocol}//${location.host}`;

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

/******************************* update style according to option *******************************/
const normalRoot = 'https://map.pathfinderwiki.com';
if(root!=normalRoot) {
  style.sprite = (style.sprite as string).replace(normalRoot, root);
  style.glyphs = style.glyphs.replace(normalRoot, root);
  (style.sources.golarion as any).url = (style.sources.golarion as any).url.replace(normalRoot, root);
}
(style.sources.golarion as any).url += '?v='+import.meta.env.VITE_DATA_HASH;

if(options.get('hideLabels') === 'true') {
  style.layers = style.layers.filter(l=>!l.id.includes('label'));
}
if(options.get('hideLocations') === 'true') {
  style.layers = style.layers.filter(l=>!l.id.includes('location'));
}
if(options.get('hideBorders') === 'true') {
  style.layers = style.layers.filter(l=>!l.id.includes('border'));
}

/************************* end of style adjustments ****************************************/


export const map = new Map({
  container: 'map-container',
  hash: 'location',
  attributionControl: false,
  pitchWithRotate: false,
  style: style,
});
export const golarionMap = new GolarionMap();
golarionMap.map = map;
//project to globe
let projection:Maplibre.PropertyValueSpecification<Maplibre.ProjectionDefinitionSpecification>;
if(options.get('projection') === 'globe') {
  projection = 'globe';
}
else if(options.get('projection') === 'mercator') {
  projection = 'mercator';
}
else {
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
map.on('style.load', () => {
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
map.addControl(new CompactAttributionControl(embedded));
let measureControl = new MeasureControl(golarionMap);
map.addControl(measureControl);
if(embedded) {
  map.addControl(new NewTab());
  //attribution._toggleAttribution();
  //map.once('load', e=>attribution._toggleAttribution());
}

makeLocationsClickable(golarionMap);
addRightClickMenu(embedded, map, measureControl);
addSpecialURLOptions(map);


//////////debugging options
//map.showTileBoundaries = true;
//map.showCollisionBoxes = true;
(window as any).map = map;
