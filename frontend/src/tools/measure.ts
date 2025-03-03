import { GeoJSONSource, IControl, LngLat, Map } from "maplibre-gl";
import { enable } from "mapbox-gl-draw-geodesic";
import MapboxDraw, { DrawCustomMode } from "@mapbox/mapbox-gl-draw";
import '@mapbox/mapbox-gl-draw/dist/mapbox-gl-draw.css';
import turfDistance from '@turf/distance';
import turfMidpoint from "@turf/midpoint";
import turfLineSegment from "@turf/line-segment";
import { LineString, GeoJSON, Feature, Point } from "geojson";

export default class MeasureControl implements IControl {
    drawCtrl: MapboxDraw;
    map: Map;

    startMeasurement(lngLat:LngLat) {
      this.drawCtrl.deleteAll();

      let lineStart = this.drawCtrl.add({type: 'LineString', coordinates: [lngLat.toArray()]})[0];
      this.drawCtrl.changeMode(this.drawCtrl.modes.DRAW_LINE_STRING, {featureId: lineStart, from: lngLat.toArray()});
    }

    onAdd(map: Map): HTMLElement {
        this.map = map;
        this.drawCtrl = new MapboxDraw({
          displayControlsDefault: false,
          userProperties: true,
          modes: enable(MapboxDraw.modes),
          styles: [{
            "id": "lines",
            "type": "line",
            "filter": ["all", ["==", "$type", "LineString"]],
            "layout": {
              "line-cap": "round",
              "line-join": "round"
            },
            "paint": {
              "line-color": "#D20000",
              "line-dasharray": [0.2, 2],
              "line-width": 2
            }
          },{
            "id": "endpoints",
            "type": "circle",
            "filter": ["all", ["==", "meta", "vertex"], ["==", "$type", "Point"]],
            "paint": {
              "circle-radius": 3,
              "circle-color": "#D20000"
            }
          },{
            'id': 'labels',
            'type': 'symbol',
            "filter": ["all", ["==", "$type", "Point"], ['has', 'user_measurement']],
            'layout': {
              'text-font': ['NotoSans-Medium'],
              'text-field': ['get', 'user_measurement'],
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
          }],
        });
        map.addControl(this.drawCtrl as any);

        map.on('load', () => {
          map.on('draw.create', this._updateLabels.bind(this));
          map.on('draw.update', this._updateLabels.bind(this));
          map.on('draw.delete', this._updateLabels.bind(this));
          //map.on('draw.render', this._updateLabels.bind(this));
        });

        return document.createElement('div');
    }
    onRemove(map: Map): void {}

    _updateLabels() {
      // Build up the centroids for each segment into a features list, containing a property 
      // to hold up the measurements
      let features:Feature<Point>[] = [];
      // Generate features from what we have on the drawControl:
      let drawnFeatures = this.drawCtrl.getAll();
      //delete old labels
      let toDelete = drawnFeatures.features.filter(f=>f.properties.measurement && f.id).map(f=>f.id as string);
      this.drawCtrl.delete(toDelete);
      drawnFeatures.features.forEach((feature) => {
        try {
          if (feature.geometry.type == 'LineString') {
            let segments = turfLineSegment(feature as Feature<LineString>);
            segments.features.forEach((segment) => {
              let a = segment.geometry.coordinates[0];
              let b = segment.geometry.coordinates[1];

              let mid = turfMidpoint(a,b);
              let dist = turfDistance(a,b);

              let label = `${toKm(dist)}\n${toMi(dist*0.621371)}`;
              let res = this.drawCtrl.add({
                ...mid,
                id: `${feature.id}-${segment.id}-mid`,
                properties: {measurement: label}
              });
              console.log(res);
            });
          }
        } catch(e) {
           //Silently ignored
        }
        
      });
    }
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