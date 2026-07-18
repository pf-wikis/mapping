// MapLibre uses a 512px logical world tile at zoom 0.
const WORLD_TILE_PX = 512;
// We treat Golarion as Earth-sized: the upstream map's own measure tool uses
// turf's Earth-radius distance math, so this matches what users see on the
// website.
const EARTH_CIRCUMFERENCE_M = 40075016.686;
const METERS_PER_MILE = 1609.344;

/** Meters per CSS pixel at a given latitude and zoom. */
export function metersPerCssPixel(latDeg: number, zoom: number): number {
  return (
    (EARTH_CIRCUMFERENCE_M * Math.cos((latDeg * Math.PI) / 180)) /
    (WORLD_TILE_PX * 2 ** zoom)
  );
}

export interface ScaleInfo {
  /** Meters per pixel of the baked image (CSS px / resolution factor). */
  metersPerImagePixel: number;
  /** Miles covered by one grid square of gridSizePx image pixels. */
  gridDistanceMiles: number;
  /** Total miles covered by the baked scene, width and height. */
  sceneWidthMiles: number;
  sceneHeightMiles: number;
}

/** Normalized web-mercator coordinates in [0,1]. */
export function mercatorNorm(lng: number, lat: number): { x: number; y: number } {
  const latRad = (lat * Math.PI) / 180;
  return {
    x: (lng + 180) / 360,
    y: (1 - Math.log(Math.tan(Math.PI / 4 + latRad / 2)) / Math.PI) / 2
  };
}

/** Project lng/lat to baked-image pixel coordinates for a given view. */
export function projectToImage(opts: {
  lng: number;
  lat: number;
  center: { lng: number; lat: number };
  zoom: number;
  cssWidth: number;
  cssHeight: number;
  resFactor: number;
}): { x: number; y: number } {
  const world = WORLD_TILE_PX * 2 ** opts.zoom;
  const p = mercatorNorm(opts.lng, opts.lat);
  const c = mercatorNorm(opts.center.lng, opts.center.lat);
  return {
    x: ((p.x - c.x) * world + opts.cssWidth / 2) * opts.resFactor,
    y: ((p.y - c.y) * world + opts.cssHeight / 2) * opts.resFactor
  };
}

/**
 * Compute a center/zoom that fits a [minX, minY, maxX, maxY] bbox into the
 * given CSS viewport with fractional padding. maxZoom caps overzoom past the
 * data's detail limit (upstream maxZoomWithData is 8).
 */
export function viewFromBbox(
  bbox: [number, number, number, number],
  cssWidth: number,
  cssHeight: number,
  { pad = 0.15, maxZoom = 8.4 }: { pad?: number; maxZoom?: number } = {}
): { center: [number, number]; zoom: number } {
  const a = mercatorNorm(bbox[0], bbox[1]);
  const b = mercatorNorm(bbox[2], bbox[3]);
  const dx = Math.abs(b.x - a.x);
  const dy = Math.abs(b.y - a.y);
  const usableW = cssWidth * (1 - pad);
  const usableH = cssHeight * (1 - pad);
  const zx = dx > 0 ? Math.log2(usableW / (WORLD_TILE_PX * dx)) : Infinity;
  const zy = dy > 0 ? Math.log2(usableH / (WORLD_TILE_PX * dy)) : Infinity;
  const zoom = Math.min(zx, zy, maxZoom);
  const cx = (a.x + b.x) / 2;
  const cy = (a.y + b.y) / 2;
  const lng = cx * 360 - 180;
  const lat = (Math.atan(Math.sinh(Math.PI * (1 - 2 * cy))) * 180) / Math.PI;
  return { center: [lng, lat], zoom };
}

export function computeScale(opts: {
  latDeg: number;
  zoom: number;
  cssWidth: number;
  cssHeight: number;
  resFactor: number;
  gridSizePx: number;
}): ScaleInfo {
  const mPerCssPx = metersPerCssPixel(opts.latDeg, opts.zoom);
  const metersPerImagePixel = mPerCssPx / opts.resFactor;
  return {
    metersPerImagePixel,
    gridDistanceMiles: (opts.gridSizePx * metersPerImagePixel) / METERS_PER_MILE,
    sceneWidthMiles: (opts.cssWidth * mPerCssPx) / METERS_PER_MILE,
    sceneHeightMiles: (opts.cssHeight * mPerCssPx) / METERS_PER_MILE
  };
}
