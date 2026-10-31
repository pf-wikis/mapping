import { addProtocol } from "maplibre-gl";
import { Protocol } from "pmtiles";
import { MODULE_ID, tilesHost } from "./settings";

let protocolRegistered = false;

/**
 * Load the generated MapLibre style (assets/style.json, produced by
 * scripts/generate-style.mjs from pf-wikis/mapping source) and point every
 * __HOST__ placeholder at the configured endpoint.
 */
export async function loadStyle(): Promise<any> {
  if (!protocolRegistered) {
    const protocol = new Protocol();
    addProtocol("pmtiles", protocol.tilev4);
    protocolRegistered = true;
  }
  const resp = await fetch(`modules/${MODULE_ID}/assets/style.json`);
  if (!resp.ok) {
    throw new Error(
      `Golarion Maps: assets/style.json missing (${resp.status}). Run "npm run style" in the module repo.`
    );
  }
  const text = await resp.text();
  const style = JSON.parse(text.replaceAll("__HOST__", tilesHost()));
  // Scene baking assumes a flat map; force mercator regardless of what the
  // upstream style defaults to (it can default to the globe projection).
  style.projection = { type: "mercator" };
  return style;
}
