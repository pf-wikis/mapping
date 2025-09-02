import { Map } from "maplibre-gl";

export class GolarionMap {
    map: Map;
    mode: 'view'|'draw-wait'|'draw-draw' = 'view';
}