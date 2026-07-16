# Hillshade for Mountains and Hills — Design Spec

## Date
2026-07-16

## Goal
Add synthetic hillshade layers for mountains and hills to the Golarion map. The hillshade must be generated automatically from 2D polygon shapes (no elevation data exists). Two separate visual layers: mountains and hills.

## Context

- The GeoPackage contains `mountains` (236 multipolygon features) and `hills` (431 multipolygon features) — **only geometry and labels, no elevation**.
- The `locations.geojson` has 42 `mountain` Points that can be merged into the mountain layer.
- The tile compiler is a Java DAG that produces vector GeoJSON data, compiled into PMTiles via Planetiler.
- The frontend is TypeScript + Vite + maplibre-gl-js with a generated virtual style (`style.ts`).
- No DEM data exists. No raster data path exists in the current pipeline.

## Chosen Approach

### Overview: Synthesized DEM from polygons → Hillshade PNGs → Raster layers

1. **DEM synthesis** from polygon distance transforms
2. **Hillshade generation** via GDAL `gdaldem`
3. **PNG export** as single world-file-aligned images
4. **Frontend**: Two `raster` layer sources with opacity blending

#### Why this approach

- **Deterministic**: Same polygons + fixed parameters → identical output PNGs.
- **Within existing tools**: Uses GT GDAL (`gdaldem`) already pulled in via the QGIS base Docker image.
- **Two separate layers**: Gives independent control over mountain vs hill shading.

## Algorithm — Synthesized DEM from Polygons

### For each polygon group (mountains, hills):

1. **Rasterize** polygons to a fixed grid (1024×512, max 120° × 60° world extent).
2. **Distance-to-boundary transform** (per polygon):
   - For each polygon cell, compute distance from the center to the closest polygon edge (Euclidean, in pixel units).
   - Normalize by the maximum distance within that polygon.
3. **Elevation mapping**: `elevation = maxElev × (distNormalized)^power`
   - Mountains: `maxElev = 255`, `power = 0.7` (sharper peaks)
   - Hills: `maxElev = 128`, `power = 1.0` (rounder, gentler)
4. **Merge all polygons** by taking the maximum elevation at each cell.
5. **Mountain points bump**: Convert `type == "mountain"` points from `locations.geojson` into small circular polygons (radius ~0.3° or 1 cell), add as gaussian elevation bumps (`peak = 200`, `sigma = 2 cells`) merged into the mountain DEM.

### Hillshading (`gdaldem hillshade`)

- Fixed parameters: `-az 315 -alt 45 -z 1` (northwest light, moderate exaggeration)
- SGrayscale 8-bit PNG output with world file.

### Determinism

| Aspect | Fixed Value |
|--------|-------------|
| Grid size | 1024 × 512 |
| Pixel scale | 120° / 1024 lon, 60° / 512 lat |
| World bounds | x: −60..60, y: −30..30 (matches known data extent) |
| GDAL options | `-az 315 -alt 45 -z 1` |
| Algorithm params | maxElev, power per layer |
| Point bump params | sigma=2 cells, peak=200 |

Input polygon geometry changes → output changes. Fixed code + fixed inputs → identical bytes.

## Pipeline Data Flow

```
                       ┌────────────────────────────────┐
                       │  tile-compiler steps.yaml      │
                       └────────────────────────────────┘
                                        │
  ┌──────────────┐    ┌──────────────────┘──────────────────┐
  │ mountains    │    │ hills                               │
  │ READ_FILE    │    │ READ_FILE                           │
  └──────┬───────┘    └──────┬──────────────────────────────┘
         │                   │
  ┌──────▼───────┐    ┌─────▼──────────────────┐
  │  locations   │    │  GENERATE_HILLSHADE      │
  │  READ_FILE   │    │  (mountains)              │
  └──────┬───────┘    └─────┬──────────────────┘
         │                  │
  ┌──────▼───────┐          │  writes PNG + .wld to
  │  GENERATE_   │          │  frontend/public/
  │  HILLSHADE   │          │
  └──────┬───────┘          │
         │                  │
         └──────┬───────────┘
                  │
         ┌────────▼────────┐
         │  PNG outputs    │
         │  in public/     │
         └────────┬────────┘
                  │
         ┌────────▼────────┐
         │  style.ts adds  │
         │  raster sources │
         │  + layers       │
         └─────────────────┘
```

## Tile Compiler Changes

### New Java classes

#### `GenerateHillshade` — StepExecutor

```java
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
@lombok.Getter @lombok.Setter
public class GenerateHillshade extends StepExecutor {
    private String layer;       // "mountains" or "hills"
    private int maxElevation;   // 255 for mountains, 128 for hills
    private double power;       // 0.7 for mountains, 1.0 for hills
    private boolean includeLocations; // true for mountains, false for hills

    @Override
    public Content process(Inputs in) throws Exception {
        // 1. Convert polygons to raster grid
        // 2. Distance transform per polygon
        // 3. Apply elevation formula
        // 4. Optionally merge point elevations from locations input
        // 5. Write GeoTIFF DEM
        // 6. Run gdaldem hillshade → PNG + world file
        // 7. Move outputs to targetDirectory
        // 8. Return Content.empty()
    }
}
```

**Implementation details:**

- Uses JTS `Polygon` / `MultiPolygon` from the GeoJSON FeatureCollection.
- Rasterization: custom Java grid filling (Winding Number / scan-line) for exact control.
- Distance transform: JTS `Densifier` + `DistanceOp` or grid-based BFS from polygon edges.
- Output GeoTIFF: use Java Image I/O with custom `GeoTiffWriter` or write ASCII DEM + call `gdal_translate`.
  *Simpler*: Write a raw binary grid + world file, then `gdal_translate` to GeoTIFF, then `gdaldem hillshade`.
- Output files named `hillshade-{layer}.png` and `hillshade-{layer}.wld`.
- Written to `Ctx.INSTANCE.getOptions().targetDirectory()` as a side effect (like `CreateSearchIndex`).

#### Step registration

`GenerateHillshade` is auto-discovered by `LCDescriptionStep` via ClassGraph. YAML step name: `GENERATE_HILLSHADE`.

### steps.yaml additions

```yaml
- name: hillshade-mountains
  steps:
    - step: GENERATE_HILLSHADE
      dependsOn:
        polygons: mountains.READ_FILE
        locations: locations.READ_FILE
      layer: mountains
      maxElevation: 255
      power: 0.7
      includeLocations: true

- name: hillshade-hills
  steps:
    - step: GENERATE_HILLSHADE
      dependsOn:
        polygons: hills.READ_FILE
      layer: hills
      maxElevation: 128
      power: 1.0
      includeLocations: false
```

_Note: hills and mountains are already read by name, so we reference those step outputs. No new READ_FILE needed._

## Frontend Changes

### `frontend/src/ml-style/style.ts`

- **Add two raster sources** in the `sources` block:

```ts
hillshadeMountains: {
  type: 'image',
  url: `${HOST}/hillshade-mountains.png`,
  coordinates: [ [-60, 30], [60, 30], [60, -30], [-60, -30] ]  // tl, tr, br, bl
},
hillshadeHills: {
  type: 'image',
  url: `${HOST}/hillshade-hills.png`,
  coordinates: [ [-60, 30], [60, 30], [60, -30], [-60, -30] ]
}
```

- **Add two raster layers** in the `layers` array, inserted after `'background'` but before `geometry` fill.

```ts
{
  id: 'hillshade-mountains',
  type: 'raster',
  source: 'hillshadeMountains',
  paint: {
    'raster-opacity': 0.35,
    'raster-fade-duration': 0
  }
},
{
  id: 'hillshade-hills',
  type: 'raster',
  source: 'hillshadeHills',
  paint: {
    'raster-opacity': 0.25,
    'raster-fade-duration': 0
  }
}
```

### `state.ts` (optional)

No new state property needed for initial implementation. If toggles are desired later:

```ts
showHillshade: 'visible' as 'visible' | 'none'
```

## Determinism Guarantees

| Risk | Mitigation |
|------|------------|
| Random number in distance transform | Use deterministic algorithm (no rand) |
| GDAL version differences in gdaldem | Same base Docker image (QGIS 3.44 → GDAL 3.x) everywhere; algorithm is stable |
| Floating-point ordering | Merge by `Math.max` (associative, commutative) |
| Grid alignment | Fixed bounds, fixed resolution, no extent auto-calculation |
| File timestamps | Output hash compared by content; rerun skipped only if content changes |

## Files to Create or Modify

| File | Action | Purpose |
|------|--------|---------|
| `tile-compiler/src/main/java/.../GenerateHillshade.java` | Create | DEM synthesis + hillshade generation |
| `tile-compiler/steps.yaml` | Modify | Add hillshade-mountains and hillshade-hills groups |
| `frontend/src/ml-style/style.ts` | Modify | Add raster sources and layers |
| `frontend/src/ml-style/state.ts` | Optionally modify | Toggle visibility (stretch) |

No changes needed to `MergeGeometry.java`, `CompileTiles.java`, or the vector tile pipeline.

## Risks and Stretch Decisions

1. **GDAL availability**: `gdaldem` ships with the QGIS Docker base. If running locally without GDAL installed, the hillshade step fails gracefully with a clear error. GDAL CLI is not available in the current local WSL environment — this is expected and acceptable since the build always runs inside the Docker image.

2. **Image extent**: coordinates `[-60, 30], [60, 30], [60, -30]` are based on the known data extent (covers all of Golarion). Must be verified against actual data bounds.

3. **Computational cost**: Distance transform on 1024×512 grid is trivial (~0.5M cells). Insignificant compared to fractal detail / Planetiler.

4. **World file format** `.wld` is plain text, not GeoTIFF header, so `gdaldem` may not auto-attach if the input is not a real GeoTIFF. **Decision**: write a proper GeoTIFF with embedded georeference using Java `javax.imageio` + WLD, or use `gdal_translate` from raw + world file to attach geoheader.
  *Simpler path*: Write raw binary + world file, then `gdal_translate` it to a temporary GeoTIFF before `gdaldem`.

## Visual Goal

- Mountains get distinct light/dark shading that looks like minor elevation relief.
- Hills get a subtler, gentler version.
- Overall effect is light enough not to interfere with border/label legibility.
- Opacities: ~0.35 mountains, ~0.25 hills (tunable).
