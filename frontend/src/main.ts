import { MultiPoint, Point } from 'geojson';
import { AttributionControl, LngLat, Map, MapLayerMouseEvent, NavigationControl, Popup, ScaleControl } from "maplibre-gl";
import 'maplibre-gl/dist/maplibre-gl.css';
import PureContextMenu from "pure-context-menu";
import layers from './layers.js';
import MeasureControl from './measure.js';
import './style.scss';

//check if running embedded
var urlParams = new URLSearchParams(window.location.hash.replace("#","?"));
const embedded = (urlParams.get('embedded') === 'true');
const mapContainer = document.getElementById("map-container");

if(embedded) {
  mapContainer.classList.add("embedded");
}

// Set up some of the distance calculation variables.
var distanceContainer = document.getElementById('distance');

var root = location.origin + location.pathname
if(!root.endsWith("/")) root += "/";
const maxZoom = parseInt(import.meta.env.VITE_MAX_ZOOM);
const dataPath = import.meta.env.VITE_DATA_PATH;

export const map = new Map({
  container: 'map-container',
  hash: 'location',
  attributionControl: false,
  style: {
    version: 8,
    sources: {
      golarion: {
        type: 'vector',
        attribution: '<a href="https://paizo.com/community/communityuse">Paizo CUP</a>, <a href="https://github.com/pf-wikis/mapping#acknowledgments">Acknowledgments</a>',
        tiles: [
          root+dataPath+'/golarion/{z}/{x}/{y}.pbf.json'
        ],
        minzoom: 0,
        maxzoom: maxZoom
      }
    },
    sprite: root+'sprites/sprites',
    layers: layers,
    glyphs: root+'fonts/{fontstack}/{range}.pbf.json',
    transition: {
      duration: 300,
      delay: 0
    }
  },
});
map.on('error', function(err) {
  console.log(err.error.message);
  document.getElementById("map-container").innerHTML = err.error.message;
});
if(!embedded) {
  map.addControl(new NavigationControl({}));
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
  compact: embedded
}));
let measureControl = new MeasureControl();
map.addControl(measureControl);

var latLong:LngLat = null;
map.on('contextmenu', function(e) {
  latLong = e.lngLat;
});

////////////////////////////////////////// make cities clickable
function showPointer() {
  map.getCanvas().style.cursor = 'pointer';
}

function hidePointer() {
  map.getCanvas().style.cursor = '';
}

map.on('mouseenter', 'city-icons', showPointer);
map.on('mouseenter', 'city-labels', showPointer);
map.on('mouseleave', 'city-icons', hidePointer);
map.on('mouseleave', 'city-labels', hidePointer);
map.on('mouseenter', 'location-icons', showPointer);
map.on('mouseenter', 'location-labels', showPointer);
map.on('mouseleave', 'location-icons', hidePointer);
map.on('mouseleave', 'location-labels', hidePointer);


const popup = new Popup();
function clickOnWikilink(e:MapLayerMouseEvent) {
  let geom = e.features[0].geometry as Point|MultiPoint;
  let coordinates:[number, number];
  let props = e.features[0].properties;

  //if this feature has multiple geometry use the closest one
  if(geom.type === 'MultiPoint') {
    coordinates = (geom.coordinates.slice() as [number,number][]).reduce((prev, curr) => {
      if(prev === undefined) {
        return curr;
      }
      let prevDist = e.lngLat.distanceTo(new LngLat(...prev));
      let currDist = e.lngLat.distanceTo(new LngLat(...curr));
      return prevDist < currDist ? prev : curr;
    });
  }
  else {
    coordinates = geom.coordinates.slice() as [number, number];
  }
   
  // Ensure that if the map is zoomed out such that multiple
  // copies of the feature are visible, the popup appears
  // over the copy being pointed to.
  while (Math.abs(e.lngLat.lng - coordinates[0]) > 180) {
    coordinates[0] += e.lngLat.lng > coordinates[0] ? 360 : -360;
  }
   
  popup
    .setLngLat(coordinates)
    .setHTML('<a href="'+props.link+'" target="_blank">'+props.Name+"</a>")
    .addTo(map);
}

map.on('click', 'city-icons',  clickOnWikilink);
map.on('click', 'city-labels', clickOnWikilink);
map.on('click', 'location-icons',  clickOnWikilink);
map.on('click', 'location-labels', clickOnWikilink);


///////////////////////////////////////// right click menu
const items = [
  {
    label: "Measure Distance",
    callback: (e:Event) => {
      measureControl.startMeasurement(latLong);
    }
  },
  {
    label: "Copy Lat/Long",
    callback: (e:Event) => {
      let text = latLong.wrap().lat.toFixed(7)+", "+latLong.wrap().lng.toFixed(7);

      if (!navigator.clipboard) {
        alert(text);
        return;
      }
      navigator.clipboard.writeText(text).then(function() {
        console.log(`Copied '${text}' into clipboard`);
      }, function(err) {
        alert(text);
      });
    },
  },
];
const menu = new PureContextMenu(mapContainer, items, {
  show: (e:Event) => {
    //only show if map itself is clicked
    return (e.target as HTMLElement).classList.contains('maplibregl-canvas');
  }
});

//////////debugging options
//map.showCollisionBoxes = true;