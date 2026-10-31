import { Map as MLMap } from "maplibre-gl";

export interface BakeOptions {
  style: any;
  center: { lng: number; lat: number };
  zoom: number;
  /** CSS size of the viewport being reproduced. */
  width: number;
  height: number;
  /** Output resolution multiplier (canvas pixels per CSS pixel). */
  pixelRatio: number;
  timeoutMs?: number;
  /** Called after the map goes idle, before capture/teardown — e.g. to query
   * rendered features for note placement. */
  onIdle?: (map: any) => void | Promise<void>;
}

/**
 * Render the given viewport offscreen with MapLibre and return it as a WebP
 * blob. pixelRatio scales output resolution without changing the geographic
 * extent, so labels/lines stay proportionate.
 */
export async function bakeViewport(opts: BakeOptions): Promise<Blob> {
  const holder = document.createElement("div");
  holder.style.cssText = `position:fixed;left:-100000px;top:0;width:${opts.width}px;height:${opts.height}px;`;
  document.body.appendChild(holder);
  let map: any = null;
  try {
    map = new MLMap({
      container: holder,
      style: opts.style,
      center: [opts.center.lng, opts.center.lat],
      zoom: opts.zoom,
      bearing: 0,
      pitch: 0,
      interactive: false,
      attributionControl: false,
      fadeDuration: 0,
      pixelRatio: opts.pixelRatio,
      canvasContextAttributes: { preserveDrawingBuffer: true }
    });

    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(
        () => reject(new Error("Timed out waiting for map render")),
        opts.timeoutMs ?? 120_000
      );
      map.once("idle", () => {
        clearTimeout(timeout);
        resolve();
      });
    });

    if (opts.onIdle) await opts.onIdle(map);

    const canvas: HTMLCanvasElement = map.getCanvas();
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (b) => (b ? resolve(b) : reject(new Error("Canvas capture failed"))),
        "image/webp",
        0.92
      )
    );
  } finally {
    try {
      map?.remove();
    } catch {
      /* ignore */
    }
    holder.remove();
  }
}
