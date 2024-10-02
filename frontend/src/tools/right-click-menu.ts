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
  
  const menu = new PureContextMenu(map.getContainer(), items, {
    show: (e:Event) => {
      //only show if map itself is clicked
      return (e.target as HTMLElement).classList.contains('maplibregl-canvas');
    }
  });
}