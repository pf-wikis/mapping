import { GeoJSONSource, IControl, LngLatLike, Map, MapMouseEvent } from "maplibre-gl";
import turfDistance from '@turf/distance';
import turfMidpoint from "@turf/midpoint";
import turfAngle from "@turf/angle";
import turfLineSegment from "@turf/line-segment";
import { AddLayerObject } from "maplibre-gl";
import { LineString, GeoJSON, Feature, Point } from "geojson";
import { GolarionMap } from "./GolarionMap";
import changeCursor from "./ChangeCursor";

type Coord = [number, number];

export default class MeasureControl implements IControl {
    map: GolarionMap;
    line: Coord[] = [];

    constructor(map: GolarionMap) {
        this.map = map;
    }

    toggleMeasurement() {
      if(this.map.mode === 'draw-draw' ||this.map.mode === 'draw-wait') {
        this.map.mode = 'view';
        changeCursor(this.map, '');
        this.line = [];
        this._updateData(this.line);
      }
      else {
        this.map.mode = 'draw-wait';
        changeCursor(this.map, 'crosshair');
        this._updateData(this.line);
      }
    }

    onAdd(map: Map): HTMLElement {
        map.on('load', ()=> {
          map.addSource('drawings', {
            type: 'geojson',
            data: {
              type: 'FeatureCollection',
              features: []
            }
          });

          let style:AddLayerObject[] = [{
            id: "lines",
            type: "line",
            source: 'drawings',
            filter: ["all", ["==", "$type", "LineString"]],
            layout: {
              "line-cap": "round",
              "line-join": "round"
            },
            paint: {
              "line-color": "#D20000",
              "line-dasharray": [0.2, 2],
              "line-width": 2
            }
          },{
            id: "control-points",
            type: "circle",
            source: 'drawings',
            filter: ['==', ['get', 'function'], 'control-point'],
            paint: {
              "circle-radius": 3,
              "circle-color": "#D20000"
            }
          },{
            id: 'labels',
            type: 'symbol',
            source: 'drawings',
            filter: ['==', ['get', 'function'], 'measurement'],
            layout: {
              'text-font': ['NotoSans-Medium'],
              'text-field': ['get', 'distance'],
              'text-variable-anchor': ['center', 'top', 'bottom', 'left', 'right'],
              'text-radial-offset': 0.5,
              'text-justify': 'right',
              'text-size': 18
            },
            'paint': {
              'text-color': '#000',
              'text-halo-color': '#fff',
              'text-halo-width': 3,
            },
          }];
          style.forEach(s=>map.addLayer(s));
          map.on('click', this._onMapClick.bind(this));
          map.on('mousemove', this._onMouseMove.bind(this));
        });

        return document.createElement('div');
    }

    _onMapClick(event: MapMouseEvent) {
      if(this.map.mode === 'draw-wait' || this.map.mode === 'draw-draw') {
        this.map.mode = 'draw-draw';
        event.preventDefault();
        this.line.push([this._normalizeLng(event.lngLat.lng), event.lngLat.lat]);
        this._updateData(this.line);
      }
    }

    _onMouseMove(event: MapMouseEvent) {
      if(this.map.mode === 'draw-draw') {
        this._updateData([...this.line, [this._normalizeLng(event.lngLat.lng), event.lngLat.lat]]);
      }
    }

    _updateData(line: Coord[]) {
      let features: Feature[] = [];

      for(let p of line) {
        features.push({ 
          type: 'Feature',
          geometry: {
            type: 'Point',
            coordinates: p
          },
          properties: {
            function: 'control-point'
          }
        });
      }

      //build geodesic line
      let points:Coord[] = [line[0]];
      for(let i = 0; i < line.length - 1; i++) {
        _geodesic(points, line[i], line[i+1]);
        points.push(line[i + 1]);
      }
      if(points.length >= 2) {
        features.push({
          type: 'Feature',
          geometry: {
            type: 'LineString',
            coordinates: points
          },
          properties: {}
        });
      }

      //build measurements
      for(let i = 0; i < line.length - 1; i++) {
        let a = line[i];
        let b = line[i + 1];
        let mid = turfMidpoint(a, b).geometry.coordinates;
        let dist = turfDistance(a, b);
        let label = `${toKm(dist)}\n${toMi(dist*0.621371)}`;
        features.push({
          type: 'Feature',
          geometry: {
            type: 'Point',
            coordinates: mid
          },
          properties: {
            function: 'measurement',
            distance: label
          }
        });
      }
      (this.map.map.getSource('drawings') as GeoJSONSource)?.setData({
        type: 'FeatureCollection',
        features: features
      });
    }

    _normalizeLng(lng: number): number {
      if(this.line.length === 0) return lng;
      let referenceLng = this.line[this.line.length - 1][0];
      let res = lng;
      while(referenceLng - res > 180) res += 360;
      while(referenceLng - res < -180) res -= 360;
      /*if(res !== lng) {
        console.log(`Normalized ${lng} to ${res}`);
      }*/
      return res;
    }

    onRemove(map: Map): void {}
}

function toKm(val:number):string {
  if(val >= 100)
    return val.toFixed(0)+' km';
  else if(val >= 10)
    return val.toFixed(1)+' km';
  else if(val >= 1)
    return val.toFixed(2)+' km';
  else
    return (1000*val).toFixed(0)+' m';
  
}

function toMi(val:number):string {
  if(val >= 100)
    return val.toFixed(0)+' mi';
  else if(val >= 10)
    return val.toFixed(1)+' mi';
  else if(val >= 1)
    return val.toFixed(2)+' mi';
  else
    return (5280*val).toFixed(0)+' ft';
  
}

function _geodesic(points: Coord[], a: Coord, b: Coord) {
  let mid = turfMidpoint(a, b).geometry.coordinates as Coord;
  let angle = turfAngle(a, mid, b, {mercator: true});
  if(angle < 178 || angle > 182) {
    _geodesic(points, a, mid);
    points.push(mid);
    _geodesic(points, mid, b);
  }
}

