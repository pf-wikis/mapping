// Headless scene-image baker: renders every region in assets/regions.json
// from the map data using MapLibre in headless Chromium (Playwright), writing
// assets/scenes/<key>.webp + assets/thumbs/<key>.webp. This is the pipeline
// step that lets CI generate the module's images straight from the mapping
// project's own build output instead of committing rendered images to git.
//
//   npm run bake -- --data <path-to-map-data> [--keys key1,key2] [--res 4]
//
// <path-to-map-data> must contain golarion.pmtiles, fonts/, sprites/ and
// search.json (the gazetteer index) — i.e. the mapping project's build
// artifacts, or a mirror made with `npm run mirror`.
//
// Note pins and scene documents are NOT regenerated here; they live in
// packs-src/ and only need refreshing when the gazetteer changes (see
// README: "Regenerating scene documents").
import { createServer } from "node:http";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, extname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));

// --- args ---
const args = process.argv.slice(2);
const argOf = (name, dflt) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : dflt;
};
const dataDir = argOf("data", join(root, "map-data"));
const onlyKeys = argOf("keys", null)?.split(",") ?? null;
const resFactor = Number(argOf("res", "4"));
if (!existsSync(join(dataDir, "golarion.pmtiles"))) {
  console.error(`no golarion.pmtiles under ${dataDir} — pass --data <map-data>`);
  process.exit(1);
}

const CSS_W = 1152;
const CSS_H = 768;
const MILES_TO_DEG_LAT = 1 / 69.09;

// --- view resolution (mirrors src/api.ts resolveView) ---
const gaz = JSON.parse(readFileSync(join(dataDir, "search.json"), "utf8"));
const entryOf = (category, label) =>
  (gaz.find((c) => c.category === category)?.entries ?? []).find((e) => e.label === label);
const bboxCenter = (b) => (b.length === 2 ? [b[0], b[1]] : [(b[0] + b[2]) / 2, (b[1] + b[3]) / 2]);

function zoomForBbox(b, w, h) {
  const dx = Math.max(b[2] - b[0], 0.0001);
  const dy = Math.max(b[3] - b[1], 0.0001);
  const midLat = (b[1] + b[3]) / 2;
  const zx = Math.log2((w * 360) / (512 * dx)) ;
  const zy = Math.log2((h * 360 * Math.cos((midLat * Math.PI) / 180)) / (512 * dy));
  return Math.min(zx, zy) - 0.15; // small margin, matches in-module fit
}

function resolveView(spec) {
  if (spec.center && spec.zoom !== undefined) {
    return { center: spec.center, zoom: spec.zoom };
  }
  const f = spec.fit;
  if (!f) throw new Error(`${spec.key}: needs center+zoom or fit`);
  const e = entryOf(f.category, f.label);
  if (!e) throw new Error(`${spec.key}: gazetteer entry not found (${f.category}/${f.label})`);
  const b = e.timed?.[0]?.bbox;
  if (f.radiusMi) {
    const [cx, cy] = bboxCenter(b);
    const dLat = f.radiusMi * MILES_TO_DEG_LAT;
    const dLng = dLat / Math.cos((cy * Math.PI) / 180);
    const bb = [cx - dLng, cy - dLat, cx + dLng, cy + dLat];
    let zoom = zoomForBbox(bb, CSS_W, CSS_H);
    if (f.maxZoom) zoom = Math.min(zoom, f.maxZoom);
    return { center: [cx, cy], zoom };
  }
  if (b.length === 2) throw new Error(`${spec.key}: point entry needs radiusMi`);
  return { center: bboxCenter(b), zoom: zoomForBbox(b, CSS_W, CSS_H) };
}

// --- static server for map data + node_modules + bake page ---
const MIME = {
  ".js": "text/javascript", ".mjs": "text/javascript", ".css": "text/css",
  ".json": "application/json", ".html": "text/html", ".pmtiles": "application/octet-stream",
  ".pbf": "application/x-protobuf", ".png": "image/png", ".webp": "image/webp"
};
const styleJson = readFileSync(join(root, "assets", "style.json"), "utf8");
const page = `<!doctype html><html><head>
<link rel="stylesheet" href="/nm/maplibre-gl/dist/maplibre-gl.css">
<style>body{margin:0}#map{width:${CSS_W}px;height:${CSS_H}px}</style>
</head><body><div id="map"></div>
<script src="/nm/pmtiles/dist/pmtiles.js"></script>
<script type="module">
import * as maplibregl from "/nm/maplibre-gl/dist/maplibre-gl.mjs";
const protocol = new pmtiles.Protocol();
maplibregl.addProtocol("pmtiles", protocol.tile);
window.__bake = (style, center, zoom, pixelRatio) => new Promise((resolve, reject) => {
  window.devicePixelRatio = pixelRatio;
  const map = new maplibregl.Map({
    container: "map", style, center, zoom, interactive: false,
    attributionControl: false, pixelRatio, preserveDrawingBuffer: true,
    fadeDuration: 0
  });
  map.on("error", (e) => reject(new Error(e.error?.message ?? "map error")));
  map.once("idle", () => {
    map.getCanvas().toBlob(async (b) => {
      const buf = new Uint8Array(await b.arrayBuffer());
      map.remove();
      resolve(Array.from(buf));
    }, "image/webp", 0.9);
  });
});
window.__ready = true;
</script></body></html>`;

const server = createServer((req, res) => {
  const url = decodeURIComponent(req.url.split("?")[0]);
  try {
    if (url === "/") {
      res.writeHead(200, { "content-type": "text/html" });
      res.end(page);
      return;
    }
    if (url === "/style.json") {
      res.writeHead(200, { "content-type": "application/json" });
      res.end(styleJson.replaceAll("__HOST__", `http://127.0.0.1:${server.address().port}/data`));
      return;
    }
    const file = url.startsWith("/nm/")
      ? join(root, "node_modules", url.slice(4))
      : url.startsWith("/data/")
        ? join(dataDir, url.slice(6))
        : null;
    if (!file || !existsSync(file)) {
      res.writeHead(404);
      res.end();
      return;
    }
    const data = readFileSync(file);
    const range = req.headers.range?.match(/bytes=(\d+)-(\d+)?/);
    if (range) {
      const start = Number(range[1]);
      const end = range[2] ? Number(range[2]) : data.length - 1;
      res.writeHead(206, {
        "content-type": MIME[extname(file)] ?? "application/octet-stream",
        "content-range": `bytes ${start}-${end}/${data.length}`,
        "content-length": end - start + 1
      });
      res.end(data.subarray(start, end + 1));
      return;
    }
    res.writeHead(200, { "content-type": MIME[extname(file)] ?? "application/octet-stream" });
    res.end(data);
  } catch (e) {
    res.writeHead(500);
    res.end(String(e));
  }
});

// --- bake loop ---
const { chromium } = await import("playwright");
await new Promise((r) => server.listen(0, "127.0.0.1", r));
const base = `http://127.0.0.1:${server.address().port}`;

const manifest = JSON.parse(readFileSync(join(root, "assets", "regions.json"), "utf8"));
const regions = manifest.regions.filter((r) => !onlyKeys || onlyKeys.includes(r.key));
mkdirSync(join(root, "assets", "scenes"), { recursive: true });
mkdirSync(join(root, "assets", "thumbs"), { recursive: true });

const browser = await chromium.launch({ args: ["--use-angle=swiftshader"] });
const ctx = await browser.newContext({ viewport: { width: CSS_W, height: CSS_H } });
const pg = await ctx.newPage();
pg.on("console", (m) => { if (m.type() === "error") console.error("page:", m.text()); });
pg.on("pageerror", (e) => console.error("page:", e.message));
await pg.goto(base + "/");
await pg.waitForFunction("window.__ready === true", { timeout: 30000 });
const style = await (await fetch(base + "/style.json")).json();
// Strip symbol layers per keepIcons policy (mirrors src/api.ts bakeRegion
// keeps symbols; scene images ship WITH labels — keep full style).
let done = 0;
for (const spec of regions) {
  const view = resolveView(spec);
  const bytes = await pg.evaluate(
    ([style, center, zoom, pr]) => window.__bake(style, center, zoom, pr),
    [style, view.center, view.zoom, resFactor]
  );
  const buf = Buffer.from(bytes);
  writeFileSync(join(root, "assets", "scenes", `${spec.key}.webp`), buf);
  // Thumbnail: 400px wide, re-encoded in-page for zero native deps.
  const thumbBytes = await pg.evaluate(async ([bytes]) => {
    const blob = new Blob([new Uint8Array(bytes)], { type: "image/webp" });
    const bmp = await createImageBitmap(blob);
    const c = document.createElement("canvas");
    c.width = 400;
    c.height = Math.round((bmp.height / bmp.width) * 400);
    c.getContext("2d").drawImage(bmp, 0, 0, c.width, c.height);
    bmp.close();
    const out = await new Promise((r) => c.toBlob((b) => r(b), "image/webp", 0.8));
    return Array.from(new Uint8Array(await out.arrayBuffer()));
  }, [bytes]);
  writeFileSync(join(root, "assets", "thumbs", `${spec.key}.webp`), Buffer.from(thumbBytes));
  done++;
  console.log(`baked ${spec.key} (${done}/${regions.length})`);
}
await browser.close();
server.close();
console.log(`done: ${done} scenes -> assets/scenes/`);
