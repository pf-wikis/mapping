import { LngLat, Map, MapLayerMouseEvent, Popup } from "maplibre-gl";
import { MultiPoint, Point } from 'geojson';

export function makeLocationsClickable(map: Map) {
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
      .setHTML(`<div class="wiki-popup"><h3><a href="${props.link}" target="_blank">${props.label}</a></h3>${props.text||''}</div>`)
      .addTo(map);
  }
  
  map.on('click', 'city-icons',  clickOnWikilink);
  map.on('click', 'city-labels', clickOnWikilink);
  map.on('click', 'location-icons',  clickOnWikilink);
  map.on('click', 'location-labels', clickOnWikilink);
}  