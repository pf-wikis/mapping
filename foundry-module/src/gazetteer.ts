import { tilesHost } from "./settings";

export interface GazetteerEntry {
  label: string;
  category: string;
  /** [minX, minY, maxX, maxY]; point entries have a 2-element [lng, lat]. */
  bbox: number[];
}

let cache: GazetteerEntry[] | null = null;

/**
 * Load the upstream search index (search.json, mirrored with the map data):
 * every named feature with its category and bbox. Powers bbox-framed region
 * fitting and note-pin naming.
 */
export async function loadGazetteer(): Promise<GazetteerEntry[]> {
  if (cache) return cache;
  const resp = await fetch(`${tilesHost()}/search.json`);
  if (!resp.ok) throw new Error(`Gazetteer fetch failed: ${resp.status}`);
  const raw = await resp.json();
  const flat: GazetteerEntry[] = [];
  for (const cat of raw) {
    for (const e of cat.entries ?? []) {
      const bbox = e.timed?.[0]?.bbox;
      if (!bbox) continue;
      flat.push({ label: e.label, category: cat.category, bbox });
    }
  }
  cache = flat;
  return flat;
}

export function entryCenter(e: GazetteerEntry): { lng: number; lat: number } {
  const b = e.bbox;
  return b.length === 2
    ? { lng: b[0], lat: b[1] }
    : { lng: (b[0] + b[2]) / 2, lat: (b[1] + b[3]) / 2 };
}

export function findEntry(
  gaz: GazetteerEntry[],
  category: string,
  label: string
): GazetteerEntry | undefined {
  return gaz.find((e) => e.category === category && e.label === label);
}

/** PathfinderWiki article URL for a feature label (MediaWiki title form). */
export function wikiUrl(label: string): string {
  return `https://pathfinderwiki.com/wiki/${encodeURIComponent(label.replaceAll(" ", "_"))}`;
}
