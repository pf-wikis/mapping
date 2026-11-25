import { NavigationControl } from "maplibre-gl";
import { GolarionMap } from "./GolarionMap";
import { ProjectionControl } from "./ProjectionControl";
import SearchControl from "./SearchControl";
import { SVGExportControl } from "@versatiles/maplibre-svg-export";
import TimeSliderControl from "./TimeSliderControl";
import LegendControl from "./LegendControl";

export function addFullVersionTools(map: GolarionMap, timeSlider: TimeSliderControl) {
  map.map.addControl(new ProjectionControl(map));
  map.map.addControl(new NavigationControl({showCompass: true}));
  map.map.addControl(timeSlider, 'top-left');
  map.map.addControl(new SearchControl(map), 'top-left');
  map.map.addControl(new SVGExportControl(), 'top-right');
  //the legend is only part of the full, non-embedded map
  map.map.addControl(new LegendControl(), 'top-right');
}