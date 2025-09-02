import { GolarionMap } from "./GolarionMap";

export default function changeCursor(map:GolarionMap, cursor:string) {
    let canv = map.map.getCanvas();
    canv.style.cursor = cursor;
}