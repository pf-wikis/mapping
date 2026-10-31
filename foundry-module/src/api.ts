import { bakeViewport } from "./bake";
import { entryCenter, findEntry, loadGazetteer, wikiUrl } from "./gazetteer";
import { computeScale, projectToImage, viewFromBbox } from "./scale";
import { buildSceneData } from "./scene-data";
import { MODULE_ID } from "./settings";
import { loadStyle } from "./style";

export interface RegionSpec {
  /** Stable id; also the image filename (<key>.webp). */
  key: string;
  name: string;
  /** Explicit view… */
  center?: [number, number];
  zoom?: number;
  /** …or fit a gazetteer feature's bbox (point features use radiusMi), or —
   * with near/radiusDeg — the union bbox of every category entry near a
   * point (e.g. all of a city's districts). */
  fit?: {
    category: string;
    label?: string;
    radiusMi?: number;
    near?: [number, number];
    radiusDeg?: number;
    maxZoom?: number;
  };
  width?: number;
  height?: number;
  resFactor?: number;
  gridType?: "gridless" | "square";
  gridSize?: number;
  /** Compendium folder name this scene belongs to. */
  folder?: string;
  /** Place named note pins for locations visible at this zoom (default true). */
  notes?: boolean;
}

const DEFAULTS = { width: 1152, height: 768, resFactor: 4, gridType: "gridless" as const, gridSize: 100, notes: true };
const MILES_TO_DEG_LAT = 1 / 69.09;

async function resolveView(spec: RegionSpec, width: number, height: number) {
  if (spec.center && spec.zoom !== undefined) {
    return { center: { lng: spec.center[0], lat: spec.center[1] }, zoom: spec.zoom };
  }
  if (!spec.fit) throw new Error(`${spec.key}: needs center+zoom or fit`);
  const gaz = await loadGazetteer();
  let bbox: [number, number, number, number];
  if (spec.fit.near) {
    const [nx, ny] = spec.fit.near;
    const r = spec.fit.radiusDeg ?? 0.35;
    const members = gaz.filter((e) => {
      if (e.category !== spec.fit!.category) return false;
      const c = entryCenter(e);
      return Math.hypot(c.lng - nx, c.lat - ny) < r;
    });
    if (!members.length) throw new Error(`${spec.key}: no ${spec.fit.category} near ${nx},${ny}`);
    bbox = [Infinity, Infinity, -Infinity, -Infinity];
    for (const e of members) {
      const b = e.bbox.length === 2 ? [e.bbox[0], e.bbox[1], e.bbox[0], e.bbox[1]] : e.bbox;
      bbox = [
        Math.min(bbox[0], b[0]),
        Math.min(bbox[1], b[1]),
        Math.max(bbox[2], b[2]),
        Math.max(bbox[3], b[3])
      ];
    }
    const view = viewFromBbox(bbox, width, height, { maxZoom: spec.fit.maxZoom ?? 8.4 });
    return { center: { lng: view.center[0], lat: view.center[1] }, zoom: view.zoom };
  }
  const entry = findEntry(gaz, spec.fit.category, spec.fit.label!);
  if (!entry) throw new Error(`${spec.key}: gazetteer entry not found (${spec.fit.category}/${spec.fit.label})`);
  if (entry.bbox.length === 2) {
    const r = spec.fit.radiusMi ?? 40;
    const dLat = r * MILES_TO_DEG_LAT;
    const dLng = dLat / Math.cos((entry.bbox[1] * Math.PI) / 180);
    bbox = [entry.bbox[0] - dLng, entry.bbox[1] - dLat, entry.bbox[0] + dLng, entry.bbox[1] + dLat];
  } else {
    bbox = entry.bbox as [number, number, number, number];
  }
  const view = viewFromBbox(bbox, width, height, { maxZoom: spec.fit.maxZoom ?? 8.4 });
  return { center: { lng: view.center[0], lat: view.center[1] }, zoom: view.zoom };
}

/**
 * Build Note document sources for locations visible in the rendered view:
 * rendered location icons are matched to gazetteer entries by proximity (the
 * tiles carry only feature ids, the gazetteer carries the names), so pins
 * appear exactly where the website shows a location at this zoom.
 */
async function buildNotes(
  map: any,
  view: { center: { lng: number; lat: number }; zoom: number },
  width: number,
  height: number,
  resFactor: number
): Promise<Record<string, unknown>[]> {
  const gaz = await loadGazetteer();
  const locations = gaz.filter((e) => e.category === "locations" && e.bbox.length === 2);
  let rendered = map
    .queryRenderedFeatures(undefined, { layers: ["location-icons"] })
    .filter((f: any) => f.geometry?.type === "Point");
  // Dense views at nation/region zoom render hundreds of generic POI dots;
  // keep settlement markers only there. At city zoom (>= 9) every marker IS
  // the content (landmarks, buildings), so never settlement-filter those.
  if (rendered.length > 60 && view.zoom < 9) {
    rendered = rendered.filter((f: any) =>
      /city|town|village|capital/.test(String(f.properties?.icon ?? ""))
    );
  }
  // Still-dense views get capped by settlement prominence so nation maps
  // stay readable (capitals/large cities always make the cut).
  const MAX_NOTES = 150;
  if (rendered.length > MAX_NOTES) {
    const prio = (icon: string) => {
      if (icon.includes("capital")) return 0;
      if (icon.includes("city-large")) return 1;
      if (icon.includes("city-medium")) return 2;
      if (icon.includes("city-small")) return 3;
      if (icon.includes("town")) return 4;
      if (icon.includes("village")) return 5;
      return 6;
    };
    rendered = rendered
      .sort((a: any, b: any) => prio(String(a.properties?.icon)) - prio(String(b.properties?.icon)))
      .slice(0, MAX_NOTES);
  }
  const notes: Record<string, unknown>[] = [];
  const seen = new Set<string>();
  // Match tolerance shrinks with zoom: 0.03° is fine at nation scale but at
  // city zoom neighboring buildings are far closer together than that. Floor
  // at ~150 m: overzoomed tiles quantize positions to the z8 tile grid
  // (~40 m), so a tighter tolerance starts missing legitimate matches.
  const threshold = Math.max(0.03 / Math.max(1, 2 ** (view.zoom - 6)), 0.0015);
  for (const f of rendered) {
    const [lng, lat] = f.geometry.coordinates;
    let best: { label: string; d: number } | null = null;
    for (const loc of locations) {
      const d = Math.hypot(loc.bbox[0] - lng, loc.bbox[1] - lat);
      if (d < threshold && (!best || d < best.d)) best = { label: loc.label, d };
    }
    if (!best || seen.has(best.label)) continue;
    seen.add(best.label);
    const px = projectToImage({
      lng,
      lat,
      center: view.center,
      zoom: view.zoom,
      cssWidth: width,
      cssHeight: height,
      resFactor
    });
    if (px.x < 0 || px.y < 0 || px.x > width * resFactor || px.y > height * resFactor) continue;
    notes.push({
      _id: foundry.utils.randomID(16),
      x: Math.round(px.x),
      y: Math.round(px.y),
      text: best.label,
      fontSize: 24,
      iconSize: 32,
      texture: { src: "icons/svg/village.svg" },
      global: true,
      flags: { [MODULE_ID]: { wikiUrl: wikiUrl(best.label) } }
    });
  }
  return notes;
}

/**
 * Render ONLY the symbol/label layers on a transparent background — the
 * overlay composited over painted art so names and pins stay crisp vectors.
 * pixelRatio defaults to the painted-art width (2528 / css 1152); the
 * composite step stretches per-axis to absorb the model's ~0.6% v-stretch.
 */
export async function bakeLabels(spec: RegionSpec, { pixelRatio = 2528 / 1152 } = {}) {
  const width = spec.width ?? DEFAULTS.width;
  const height = spec.height ?? DEFAULTS.height;
  const view = await resolveView(spec, width, height);
  const style = await loadStyle();
  style.layers = style.layers.filter((l: any) => l.type === "symbol");
  const blob = await bakeViewport({
    style,
    center: view.center,
    zoom: view.zoom,
    width,
    height,
    pixelRatio
  });
  return { blob, view };
}

/**
 * Render a region WITHOUT any symbol/label/icon layers — the clean geometry
 * base used as input for AI stylization. resFactor is fixed low: the model
 * consumes ~1-2K inputs; full bake resolution would be wasted.
 */
export async function bakeBase(spec: RegionSpec, { resFactor = 1.5 } = {}) {
  const width = spec.width ?? DEFAULTS.width;
  const height = spec.height ?? DEFAULTS.height;
  const view = await resolveView(spec, width, height);
  const style = await loadStyle();
  // keepIcons: town-detail maps without district outlines keep the settlement
  // marker icons (text still stripped) so AI stylization knows where the
  // town sits; everything else gets pure geometry.
  style.layers = style.layers.filter((l: any) =>
    (spec as any).keepIcons
      ? l.type !== "symbol"
      : l.type !== "symbol" && l.id !== "location-icons"
  );
  const blob = await bakeViewport({
    style,
    center: view.center,
    zoom: view.zoom,
    width,
    height,
    pixelRatio: resFactor
  });
  return { blob, view };
}

/** Render one region spec to a WebP blob + matching scene document source. */
export async function bakeRegion(spec: RegionSpec, imagePath: string) {
  const { width, height, resFactor, gridType, gridSize, notes } = { ...DEFAULTS, ...spec };
  const view = await resolveView(spec, width, height);
  const style = await loadStyle();
  let noteDocs: Record<string, unknown>[] = [];
  const blob = await bakeViewport({
    style,
    center: view.center,
    zoom: view.zoom,
    width,
    height,
    pixelRatio: resFactor,
    onIdle: notes
      ? async (map) => {
          noteDocs = await buildNotes(map, view, width, height, resFactor);
        }
      : undefined
  });
  const scale = computeScale({
    latDeg: view.center.lat,
    zoom: view.zoom,
    cssWidth: width,
    cssHeight: height,
    resFactor,
    gridSizePx: gridSize
  });
  const gridDistanceMiles =
    gridType === "square"
      ? scale.gridDistanceMiles
      : (100 * scale.metersPerImagePixel) / 1609.344;
  const sceneData = buildSceneData({
    name: spec.name,
    width: Math.round(width * resFactor),
    height: Math.round(height * resFactor),
    imagePath,
    gridType,
    gridSize,
    gridDistanceMiles
  });
  if (noteDocs.length) sceneData.notes = noteDocs;
  return { blob, sceneData, scale, view, noteCount: noteDocs.length };
}

/** Downscale a baked image blob to a sidebar/compendium thumbnail. */
export async function makeThumb(blob: Blob, targetWidth = 400): Promise<Blob> {
  const bmp = await createImageBitmap(blob);
  const scale = targetWidth / bmp.width;
  const canvas = document.createElement("canvas");
  canvas.width = targetWidth;
  canvas.height = Math.round(bmp.height * scale);
  canvas.getContext("2d")!.drawImage(bmp, 0, 0, canvas.width, canvas.height);
  bmp.close();
  return new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("thumb failed"))), "image/webp", 0.8)
  );
}

/**
 * Compendium-generation driver (dev tooling, run from a GM session): bakes
 * every region, uploads webps into <uploadDir> for later copying into the
 * repo, and returns scene document sources pointing at the module's shipped
 * assets path. Each doc carries __key and __folder passthroughs for the pack
 * builder. Every scene gets a shipped thumbnail (assets/thumbs/<key>.webp).
 */
export async function generateScenes(
  regions: RegionSpec[],
  { uploadDir = "golarion-maps-build", onProgress }: { uploadDir?: string; onProgress?: (msg: string) => void } = {}
) {
  const FP = foundry.applications.apps.FilePicker.implementation;
  await FP.createDirectory("data", uploadDir).catch(() => {});
  const docs: Record<string, unknown>[] = [];
  for (const spec of regions) {
    onProgress?.(`baking ${spec.key}…`);
    const shippedPath = `modules/${MODULE_ID}/assets/scenes/${spec.key}.webp`;
    const { blob, sceneData, noteCount } = await bakeRegion(spec, shippedPath);
    const file = new File([blob], `${spec.key}.webp`, { type: "image/webp" });
    const uploaded = await FP.upload("data", uploadDir, file, {}, { notify: false });
    if (!uploaded?.path) throw new Error(`upload failed for ${spec.key}`);
    const thumb = await makeThumb(blob);
    await FP.upload(
      "data",
      uploadDir,
      new File([thumb], `${spec.key}-thumb.webp`, { type: "image/webp" }),
      {},
      { notify: false }
    );
    sceneData.thumb = `modules/${MODULE_ID}/assets/thumbs/${spec.key}.webp`;
    (sceneData as any).__key = spec.key;
    (sceneData as any).__folder = spec.folder ?? null;
    docs.push(sceneData);
    onProgress?.(`done ${spec.key} (${noteCount} notes)`);
  }
  return docs;
}
