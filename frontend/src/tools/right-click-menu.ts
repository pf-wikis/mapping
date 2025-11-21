import { LngLat, Map } from "maplibre-gl";
import MeasureControl from "./measure";
import PureContextMenu from "pure-context-menu";

export function addRightClickMenu(embedded: boolean, map: Map, measureControl: MeasureControl) {
  var latLong:LngLat = null;
  map.on('contextmenu', function(e) {
    latLong = e.lngLat;
  });

  let items = [
    {
      label: "Log Terrain Data at Point",
      callback: (e:Event) => {
        // Get the pathfinder instance from global scope
        const pathfinder = (window as any).pathfinder;
        if (pathfinder && latLong) {
          console.log(`=== TERRAIN ANALYSIS AT CLICKED POINT ===`);
          pathfinder.testTerrainAtCoordinate(latLong.lng, latLong.lat);
          console.log(`Clicked at: [${latLong.lng.toFixed(6)}, ${latLong.lat.toFixed(6)}]`);
        } else {
          console.warn('Pathfinder not available or no coordinates');
        }
      }
    },
    {
      label: "Toggle Measure Distance",
      callback: (e:Event) => {
        measureControl.toggleMeasurement(latLong);
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
  
  const menu = new PureContextMenu(map.getContainer(), items, {
    show: (e:Event) => {
      //only show if map itself is clicked
      return (e.target as HTMLElement).classList.contains('maplibregl-canvas');
    }
  });
}