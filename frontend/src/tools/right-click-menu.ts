import { LngLat } from "maplibre-gl";
import MeasureControl from "./measure";
import PureContextMenu from "pure-context-menu";
import { Options } from "../URLOptions";
import { GolarionMap } from "./GolarionMap";

export function addRightClickMenu(map: GolarionMap, measureControl: MeasureControl) {

  function generateItems(menu: any) {
    let items = [
      {
        label: "Toggle Measure Distance",
        callback: (e:Event) => {
          measureControl.toggleMeasurement();
        }
      },
      {
        label: "Copy Lat/Long",
        callback: (e:Event) => {
          if(!latLong) return;
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

    if(map.options.highlight) {
      items.push({
        label: "Remove Highlight",
        callback: (e:Event) => {
          map.options.highlight = undefined;
          generateItems(menu);
        },
      });
    }
    menu.setItems(items);
  }

  var latLong:LngLat|null = null;
  map.map.on('contextmenu', function(e) {
    latLong = e.lngLat;
  });
  
  const menu = new PureContextMenu(map.map.getContainer(), [], {
    show: (e:Event) => {
      //only show if map itself is clicked
      return (e.target as HTMLElement).classList.contains('maplibregl-canvas');
    }
  });

  generateItems(menu);
}