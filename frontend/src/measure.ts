import { ControlPosition, GeoJSONSource, IControl, LngLat, Map } from "maplibre-gl";
import { enable } from "mapbox-gl-draw-geodesic";
import MapboxDraw, { DrawCustomMode } from "@mapbox/mapbox-gl-draw";
import '@mapbox/mapbox-gl-draw/dist/mapbox-gl-draw.css';
import turfLength from '@turf/length';
import turfCentroid from "@turf/centroid";
import turfLineSegment from "@turf/line-segment";
import { Feature, Point } from "@turf/helpers";
import { LineString } from "geojson";

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
              'text-font': ['NotoSans-Medium'],
              'text-field': ['get', 'measurement'],
              'text-variable-anchor': ['top', 'bottom', 'left', 'right'],
              'text-radial-offset': 0.5,
              'text-justify': 'auto',
              'text-letter-spacing': 0.05,
              'text-size': 18
            },
            'paint': {
              'text-color': '#D20C0C',
              'text-halo-color': '#fff',
              'text-halo-width': 10,
            },
          });
        });
        map.on('draw.create', this._updateLabels.bind(this));
        map.on('draw.update', this._updateLabels.bind(this));
        map.on('draw.delete', this._updateLabels.bind(this));
        map.on('draw.render', this._updateLabels.bind(this));

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
              let centroid = turfCentroid(segment);
              let lineLength = (turfLength(segment) * 1000)+ 'km';
              let measurement = `${lineLength}`;
              centroid.properties = {
                measurement,
              };
              features.push(centroid);
            });
          }
        } catch(e) {
           //Silently ignored
        }
        
      });
      let data = {
        type: "FeatureCollection",
        features: features
      };
      source.setData(data);
    }

}