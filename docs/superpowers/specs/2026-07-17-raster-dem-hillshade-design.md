# Raster-DEM Multi-Zoom Hillshade Design

## Date
2026-07-17

## Overview

Replace the current preshaded hillshade PNG overlay (`image` source + `raster` layers) with a proper **raster-dem** source and **hillshade** layers in maplibre-gl. The tile compiler will generate **multi-zoom terrarium-encoded DEM tiles** (not preshaded images) from the synthetic polygon DEM. The maximum zoom level will be configurable in `steps.yaml`.

## Why Raster-DEM Tiles

- **Runtime control**: Hillshade lighting direction, altitude, and exaggeration can be changed via style paint properties without recompiling tiles.
- **Multi-zoom detail**: A fixed 2048×1024 image cannot provide crisp detail when the map zooms in. DEM tiles load the appropriate resolution for each zoom.
- **Proper data**: The frontend receives actual elevation data, not a pre-baked image.

## DEM Encoding: Terrarium

Maplibre-gl `raster-dem` supports three encodings:
- `"mapbox"` (default) — uses a different decode formula
- `"terrarium"` — Mapzen Terrarium, widely documented
- `"custom"` — requires red/green/blue factor overrides

**Decision**: Use `"terrarium"`. Encode integer elevation `E` (0..255) as:
```
value = E + 32768
R = floor(value / 256)    // = 128 for E in 0..255
G = value % 256           // = E
B = 0
```
This stores our synthetic 0..255 range in the green channel with `R=128`, `B=0`.

## Tile Format: XYZ Directory

**Decision**: Output tiles as an XYZ directory tree: `hillshade-{layer}/{z}/{x}/{y}.png`

- `raster-dem` sources require tile URLs with `{z}/{x}/{y}` placeholders.
- PMTiles is **not documented** as a supported format for `raster-dem` (it is only documented for vector sources). Do NOT use PMTiles for DEM tiles.
- The frontend source will use `tiles: ["${HOST}/hillshade-{layer}/{z}/{x}/{y}.png"]`.
- `tileSize` defaults to 256 in both the source and the compiler; the pipeline generates 256×256 tiles.

### Web Mercator Tile Pyramid

Tiles must align with the Web Mercator (EPSG:3857) XYZ tile grid used by maplibre:
- Tile `(z, x, y)` bounds are computed from the global Web Mercator square `[-πR, πR]` in both axes.
- Each tile at zoom `z` covers `(2πR / 2^z)` width in the x-axis.
- The `y` axis is flipped (0 is north).

The Java code must compute which `(z, x, y)` tiles intersect the data bounds, then generate a DEM for each tile in **Web Mercator meter** space.

### Number of Tiles

For the Golarion data extent (~170° × 82° lat/lon), the intersecting tile count per zoom (512 px tiles) is approximately:

| Zoom | Tiles (mountains) | Tiles (hills) |
|------|-------------------|---------------|
| 0    | 1                 | 1             |
| 1    | 2                 | 2             |
| 2    | 4–6               | 6–10          |
| 3    | 8–16              | 16–30         |
| 4    | 20–40             | 40–80         |
| 5    | 60–120            | 120–300       |
| 6    | 200–500           | 400–1000      |

Total tiles for maxZoom=5: ~500–1500 tiles per layer. Mostly small because mountains/hills are sparse.

## Zoom Strategy

1. For each zoom `z` from `0` to `maxZoom` (configured in `steps.yaml`):
   1. Compute the set of `(x, y)` tiles that intersect the data bounds.
   2. For each tile:
      1. Compute the tile’s Web Mercator meter bounds.
      2. Convert the tile bounds to lat/lon.
      3. Find input polygons that intersect those lat/lon bounds.
      4. Convert those polygons to Web Mercator coordinates.
      5. Build a `HillshadeGrid` with:
         - `width = tileSize` (e.g., 512)
         - `height = tileSize`
         - bounds = Web Mercator tile bounds in meters
         - input polygons = intersecting polygons in Web Mercator meters
      6. Terrarium-encode the DEM grid into PNG.
      7. Write to `targetDirectory/hillshade-{layer}/{z}/{x}/{y}.png`.

2. `HillshadeGrid` is unit-agnostic — as long as polygon coordinates and bounds share the same unit system, the distance transform and elevation formula work correctly.

### Key Formulas

Lat/lon → Web Mercator meters (WGS-84 ellipsoid, R = 6,378,137):
```
x = R × lon_rad
y = R × ln(tan(π/4 + lat_rad/2))
```

Web Mercator meters → tile index:
```
n = 2^z
x_tile = floor((x + πR) / (2πR) × n)
y_tile = floor((1 - (y + πR) / (2πR)) × n)
```

For a tile `(z, x_tile, y_tile)`, bounds in meters:
```
tile_width = 2πR / n
min_x = x_tile × tile_width - πR
max_x = (x_tile + 1) × tile_width - πR
min_y = πR - (y_tile + 1) × tile_width   // note: y inverted
max_y = πR - y_tile × tile_width
```

## Java Pipeline Changes

### `HillshadeGrid.java`

Add a new public method:
```java
public void writeDemTile(File pngFile) throws IOException
```
This writes the DEM values as a terrarium-encoded RGB PNG (512×512 or configurable `tileSize`), without pre-shading. The existing `writeHillshade()` remains available during migration.

### `GenerateHillshade.java`

Add new configurable fields:
```java
private int maxZoom = 4;          // NEW: max zoom level for tiles
private int tileSize = 512;       // NEW: pixels per tile (must match source tileSize)
```

Modify `process()` to:
1. Read polygons in lat/lon (same as today).
2. Compute the data bounds in lat/lon and project to Web Mercator to find the tile pyramid coverage.
3. Loop `z = 0 .. maxZoom`:
   - Compute all `(tx, ty)` tiles intersecting the data bounds.
   - For each tile:
     a. Compute lat/lon bounds of tile (inverse Mercator).
     b. Filter polygons that intersect those bounds.
     c. Convert filtered polygons to Web Mercator meters.
     d. Build `HillshadeGrid(tileSize, tileSize, webMercatorBounds, ..., polygons_wm, ...)`.
     e. Call `grid.writeDemTile(outputFile)`.
4. Keep the bounds TypeScript emitter so the frontend knows the `bounds` of the `raster-dem` source.

### `steps.yaml`

Add to the existing `hillshade-mountains` and `hillshade-hills` groups:
```yaml
- name: hillshade-mountains
  steps:
    - step: GENERATE_HILLSHADE
      dependsOn:
        polygons: mountains.ADD_FRACTAL_DETAIL
        locations: locations.READ_FILE
      layer: mountains
      maxElevation: 255
      power: 0.7
      includeLocations: true
      maxZoom: 5          # NEW
      tileSize: 512       # NEW
      # ... other fields unchanged
```

Remove `gridWidth` and `gridHeight` (or ignore them — they’re no longer used when generating tiles; `tileSize` drives resolution per tile).

## Frontend Style Changes

### `style.ts`

Replace the two `image` sources with two `raster-dem` sources:
```typescript
hillshadeMountains: {
  type: 'raster-dem',
  encoding: 'terrarium',
  tileSize: 512,
  tiles: [`${HOST}/hillshade-mountains/{z}/{x}/{y}.png`],
  minzoom: 0,
  maxzoom: 5,  // match steps.yaml
  bounds: [-70, -40, 100, 42],
  attribution: ''
},
hillshadeHills: {
  type: 'raster-dem',
  encoding: 'terrarium',
  tileSize: 512,
  tiles: [`${HOST}/hillshade-hills/{z}/{x}/{y}.png`],
  minzoom: 0,
  maxzoom: 5,
  bounds: [-70, -40, 100, 42]
}
```

Replace the two `raster` layers with two `hillshade` layers:
```typescript
{
  id: 'hillshade-mountains',
  type: 'hillshade',
  source: 'hillshadeMountains',
  paint: {
    'hillshade-exaggeration': 30,
    'hillshade-shadow-color': 'rgba(0,0,0,0.3)',
    'hillshade-highlight-color': 'rgba(255,255,255,0.2)',
    'hillshade-accent-color': 'rgba(0,0,0,0.1)',
    'hillshade-illumination-direction': 315,
    'hillshade-illumination-altitude': 45
  }
},
{
  id: 'hillshade-hills',
  type: 'hillshade',
  source: 'hillshadeHills',
  paint: {
    'hillshade-exaggeration': 20,
    'hillshade-shadow-color': 'rgba(0,0,0,0.2)',
    'hillshade-highlight-color': 'rgba(255,255,255,0.15)',
    'hillshade-accent-color': 'rgba(0,0,0,0.05)',
    'hillshade-illumination-direction': 315,
    'hillshade-illumination-altitude': 45
  }
}
```

Note: `hillshade` layers do **not** support a direct `opacity` paint property. Opacity is approximated by tuning shadow/highlight color alpha and exaggeration.

### `state.ts` (optional)

Add a `showHillshade` property if toggling is desired. Out of scope for this change unless the user requests it.

## File Outputs

After compilation:
```
frontend/public/
  hillshade-mountains/
    0/0/0.png
    1/...
    ...
  hillshade-hills/
    0/0/0.png
    1/...
    ...
frontend/gen/
  hillshade-bounds-mountains.ts  # unchanged
  hillshade-bounds-hills.ts      # unchanged
```

Remove old outputs (or allow them to be overwritten):
- `frontend/public/hillshade-mountains.png`
- `frontend/public/hillshade-mountains.wld`
- `frontend/public/hillshade-hills.png`
- `frontend/public/hillshade-hills.wld`

## Testing Strategy

1. **Unit tests for terrarium encoding**: Verify round-trip encode/decode for elevations 0..255.
2. **Unit tests for tile bounds**: Verify that computed `(z, x, y)` bounds correctly contain known lat/lon points.
3. **Unit tests for per-tile DEM**: Generate a known simple polygon at zoom 2, verify tile PNG bytes decode to expected elevation grid.
4. **Integration test (if real data available)**: Run full pipeline for `maxZoom=2`, verify all expected tiles exist.
5. **Frontend typecheck**: `npx tsc --noEmit` in `frontend/` must pass.
6. **Java compilation**: `mvn -B compile` must pass.

## Build Performance

Per-tile DEM generation at moderate zoom scales well:
- Most tiles contain 0–5 polygons at high zoom.
- A 512×512 tile with 5 polygons ≈ a few hundred ms in Java.
- Total time for maxZoom=5: < 5 minutes per layer.
- The build already takes minutes; this adds a small fraction.

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| `hillshade` layer has no opacity property | Tune via exaggeration + shadow/highlight alpha |
| JTS `contains()` performance at high zooms | Limit maxZoom to 5–6 in steps.yaml; profile if needed |
| Web Mercator projection distorts poleward features | Synthetic DEM is already approximate; acceptable |
| Large number of small PNG files | Use `.gitignore`; tiles are generated, not committed |
| Map panning outside bounds loads empty tiles | `bounds` property on `raster-dem` prevents fetches |

## Open Questions

1. Exact `hillshade-exaggeration` values needed to match current visual appearance at 0.35/0.25 opacity. Requires visual tuning after implementation. Start with 30 (mountains) and 20 (hills).
2. Whether Java2D `Area.contains()` might be faster than JTS for rasterization. Can be optimized later without changing architecture.
3. Whether we should support `maxZoom` per-layer differently in steps.yaml. Currently one `maxZoom` field per `GENERATE_HILLSHADE` step is sufficient.
