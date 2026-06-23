import { LngLat, MapGeoJSONFeature, MapLayerMouseEvent, MapMouseEvent, Popup } from "maplibre-gl";
import { MultiPoint, Point } from 'geojson';
import { GolarionMap } from "./GolarionMap";
import changeCursor from "./ChangeCursor";

function clickableFeature(e:MapMouseEvent & {features?: MapGeoJSONFeature[];}) {
  for(let f of e.features || []) {
    if(f.properties?.fid !== undefined)
      return f;
  }
}

const loaded = Array<boolean>(10).fill(false);
const texts = new Map<number, string>();

async function getTextForFeature(feature:MapGeoJSONFeature) {
  let id = feature.properties?.fid as number|undefined;
  if(id === undefined) return undefined;
  let slice = id%10;

  if(!loaded[slice]) {
    let response = await fetch(`extra/${slice}.json?v=${BUILD_DATA_HASH}`);
    if (!response.ok) {
      throw new Error(`Failed to load location texts: ${response.statusText}`);
    }
    let extra = await response.json() as Record<number, string>;
    for(let [id, text] of Object.entries(extra)) {
      texts.set(parseInt(id), text);
    }
    loaded[slice] = true;
  }
  
  return texts.get(id);
}

export function makeLocationsClickable(gmap: GolarionMap) {
  let map = gmap.map;
  function showPointer(e:MapMouseEvent & {features?: MapGeoJSONFeature[];}) {
    if(gmap.mode !== 'view') return;
    let f = clickableFeature(e);
    if(f) changeCursor(gmap, 'pointer');
  }
  
  function hidePointer() {
    if(gmap.mode === 'view')
      changeCursor(gmap, '');
  }
  
  map.on('mouseenter', 'location-icons', showPointer);
  map.on('mouseenter', 'location-labels', showPointer);
  map.on('mouseleave', 'location-icons', hidePointer);
  map.on('mouseleave', 'location-labels', hidePointer);
  
  
  const popup = new Popup();
  async function clickOnWikilink(e:MapLayerMouseEvent) {
    if(gmap.mode !== 'view') return;
    let feature = clickableFeature(e);
    if(!feature || !feature.properties.fid) return;
    let text = await getTextForFeature(feature);
    if(!text) return;
    let geom = feature.geometry as Point|MultiPoint;
    let coordinates:[number, number];
    let props = feature.properties;
  
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
      .setHTML(`<div class=\"wiki-popup\">${text}</div>`)
      .addTo(map);
  }
  
  map.on('click', 'city-icons',  clickOnWikilink);
  map.on('click', 'city-labels', clickOnWikilink);
  map.on('click', 'location-icons',  clickOnWikilink);
  map.on('click', 'location-labels', clickOnWikilink);
}  