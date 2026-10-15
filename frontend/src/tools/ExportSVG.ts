import { ControlPosition, IControl, Map } from "maplibre-gl";
import { GolarionMap } from "./GolarionMap";
import { projection as defaultProjection } from "../ml-style/projection";
import { SVGMapRenderer } from "@versatiles/maplibre-svg-export";
import { Protocol } from "pmtiles";

export class ExportSVG implements IControl {

    private pmtiles: Protocol;

    constructor(pmtiles: Protocol) {
        this.pmtiles = pmtiles;
    }

    onAdd(map: Map): HTMLElement {
        var el = new DOMParser().parseFromString('<div class="maplibregl-ctrl maplibregl-ctrl-group"><button type="button" class="svg-export-btn" title="Export SVG"><svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"></path></svg></button></div>', 'text/html').body.firstElementChild as HTMLElement;
        el.getElementsByTagName('button')[0].addEventListener('click', async () => {
            let svg = new SVGMapRenderer({
                style: map.getStyle(),
                labels: 'none',
                renderLabels: false,
                tileCacheSize: 0,
                fetch: (url: string) => this.fetchResource(url)
            }).renderSVG({

            });

            console.log(await svg);
        });
        return el;
    }

    private async fetchResource(url: string): Promise<Response> {
        if (!url.startsWith("pmtiles://")) {
            return fetch(url);
        }

        //if the url ends with .pmtiles the metadata json is expected
        if(url.endsWith('.pmtiles')) {
            return new Response(JSON.stringify({
                tilejson: "3.0.0",
                scheme: "xyz",
                tiles: [`${url}/{z}/{x}/{y}`],
                minzoom: 0,
                maxzoom: 18,
                bounds: [ -180, -85, 180, 85 ]
            }), {
                headers: { "Content-Type": "application/json" },
            });
        }

        const response = await this.pmtiles.tile(
            { url },
            new AbortController(),
        );

        let data = response.data;
        const bytes = new Uint8Array(data as ArrayBuffer);
        console.log("Tile response", {
            url,
            length: bytes.length,
            firstBytes: Array.from(bytes.slice(0, 12)),
        });

        return new Response( data as BodyInit);
    }


    onRemove(map: Map): void {}
    getDefaultPosition?: (() => ControlPosition) | undefined;
}