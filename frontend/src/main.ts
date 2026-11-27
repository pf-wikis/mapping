import { addProtocol, Map, ScaleControl, setWorkerUrl } from "maplibre-gl";
import type { AddProtocolAction } from "maplibre-gl";
import 'maplibre-gl/dist/maplibre-gl.css';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import './style.scss';

import style from 'virtual:style';
import MeasureControl from './tools/measure.js';

import { FetchSource, PMTiles, Protocol, type Source } from 'pmtiles';
import { pmtilesDecompress } from './PmTilesDecompress.js';
import { makeLocationsClickable } from "./tools/location-popup.js";
import { addRightClickMenu } from "./tools/right-click-menu.js";
import { CachedSource } from "./CachedPmTiles.js";
import { CompactAttributionControl } from "./tools/CompactAttributionControl.js";
import { GolarionMap } from "./tools/GolarionMap.js";
import TimeSliderControl from "./tools/TimeSliderControl.js";
import { startupOptions } from "./URLOptions.js";
import { addSpecialURLOptions } from "./tools/special-url-options";
import { debug } from "./utils/debug";
import NewTab from "./tools/NewTab.js";

var root = `${location.protocol}//${location.host}`;

let pmtilesProt = new Protocol({errorOnMissingTile: false});
//add custom tile caching
let sourceUrl: string = style.sources.golarion.url;
if(sourceUrl.startsWith("pmtiles://"))
  sourceUrl = sourceUrl.substring(10);
let pmtilesSource: Source = new FetchSource(sourceUrl);
if(indexedDB) {
  try {
    pmtilesSource = new CachedSource(sourceUrl);
  } catch(e) {
    console.log("Failed to initialize IndexDB cache")
    console.log(e)
  }
}
pmtilesProt.add(new PMTiles(pmtilesSource, undefined, pmtilesDecompress));
addProtocol("pmtiles", pmtilesProt.tilev4 as AddProtocolAction);
setWorkerUrl(workerUrl);

/******************************* update style according to option *******************************/

if(!startupOptions.embedded) {
  document.getElementById('map-container')!.classList.remove("embedded");
}
if(debug)
  console.log("Effective style", style);

/************************* end of style adjustments ****************************************/


export const map = new Map({
  container: 'map-container',
  hash: 'location',
  attributionControl: false,
  pitchWithRotate: startupOptions.embedded?false:true,
  style: style,
  pixelRatio: Math.max(window.devicePixelRatio || 1, 2),
  validateStyle: debug,
  canvasContextAttributes: {
    // Enable preserveDrawingBuffer if any screenshot feature is added; it is
    // required for reading pixels back from the canvas (e.g. toDataURL()).
    // preserveDrawingBuffer: true,
  }
});
export const golarionMap = new GolarionMap(map);

//diable rotation
map.dragRotate.disable();
map.touchZoomRotate.disableRotation();

map.on('error', function(err) {
  console.log(err.error.message);
});
let timeSlider = new TimeSliderControl(golarionMap);

addSpecialURLOptions(golarionMap);

if(!startupOptions.embedded) {
  const [{ addFullVersionTools }] = await Promise.all([
    import('./tools/FullVersionTools.js'),
  ]);
  addFullVersionTools(golarionMap, timeSlider);
}
else {
  map.addControl(new NewTab());
}
map.addControl(new ScaleControl({
  unit: 'imperial',
  maxWidth: startupOptions.embedded?50:100,
}));
map.addControl(new ScaleControl({
  unit: 'metric',
  maxWidth: startupOptions.embedded?50:100,
}));
map.addControl(new CompactAttributionControl(startupOptions.embedded));

//controls are loaded on demand to keep the initial JS bundle small
let measureControl = new MeasureControl(golarionMap);
map.addControl(measureControl);

makeLocationsClickable(golarionMap);
addRightClickMenu(golarionMap, measureControl);

//change label orientation if bearing != 0
function changeStyleWithBearing() {
  golarionMap.setState('rotated', map.getBearing() !== 0);
}
map.on('rotateend', changeStyleWithBearing);
map.on('style.load', changeStyleWithBearing);


//////////debugging options
//map.showTileBoundaries = true;
//map.showCollisionBoxes = true;
if (debug) {
  (window as any).map = map;
  (window as any).MAP_VERSION = BUILD_DATA_HASH;
}
