import { GeoJSONSource, IControl, LngLat, Map } from "maplibre-gl";
import { enable } from "mapbox-gl-draw-geodesic";
import MapboxDraw, { DrawCustomMode } from "@mapbox/mapbox-gl-draw";
import '@mapbox/mapbox-gl-draw/dist/mapbox-gl-draw.css';
import turfDistance from '@turf/distance';
import turfMidpoint from "@turf/midpoint";
import turfLineSegment from "@turf/line-segment";
import { Feature, Point } from "@turf/helpers";
import { LineString, GeoJSON } from "geojson";

const DRAW_LABELS_SOURCE = 'draw-labels-source';
const DRAW_LABELS_LAYER = 'draw-labels-layer';

export default class MeasureControl implements IControl {
    drawCtrl: MapboxDraw;
    map: Map;

    startMeasurement(lngLat:LngLat) {
      this.drawCtrl.deleteAll();

      let lineStart = this.drawCtrl.add({type: 'LineString', coordinates: [lngLat.toArray()]})[0];
      console.log(lngLat);
      this.drawCtrl.changeMode(this.drawCtrl.modes.DRAW_LINE_STRING, {featureId: lineStart, from: lngLat.toArray()});
    }

    onAdd(map: Map): HTMLElement {
        this.map = map;
        let modes:{ [modeKey: string]: DrawCustomMode } = enable(MapboxDraw.modes);
        this.drawCtrl = new MapboxDraw({ displayControlsDefault: false, modes });
        map.addControl(this.drawCtrl as any);

        map.on('load', () => {
          map.addSource(DRAW_LABELS_SOURCE, {
            type: 'geojson',
            data: {
              type: "FeatureCollection",
              features: []
            }
          });
          map.addLayer({
            'id': DRAW_LABELS_LAYER,
            'type': 'symbol',
            'source': DRAW_LABELS_SOURCE,
            'layout': {
              'text-font': ['Alegreya-Regular'],
              'text-field': ['get', 'measurement'],
              'text-variable-anchor': ['center', 'top', 'bottom', 'left', 'right'],
              'text-radial-offset': 0.5,
              'text-justify': 'right',
              'text-size': 18
            },
            'paint': {
              'text-color': '#000',
              'text-halo-color': '#fff',
              'text-halo-width': 4,
            },
          });
          map.on('draw.create', this._updateLabels.bind(this));
          map.on('draw.update', this._updateLabels.bind(this));
          map.on('draw.delete', this._updateLabels.bind(this));
          map.on('draw.render', this._updateLabels.bind(this));
        });

        return document.createElement('div');
    }
    onRemove(map: Map): void {}

    _updateLabels() {
      let source = this.map.getSource(DRAW_LABELS_SOURCE) as GeoJSONSource;
      // Build up the centroids for each segment into a features list, containing a property 
      // to hold up the measurements
      let features = [] as Feature<Point>[];
      // Generate features from what we have on the drawControl:
      let drawnFeatures = this.drawCtrl.getAll();
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
              mid.properties = {
                measurement: label,
              };
              features.push(mid);
            });
          }
        } catch(e) {
           //Silently ignored
        }
        
      });
      let data:GeoJSON = {
        type: "FeatureCollection",
        features: features
      };
      source.setData(data);
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