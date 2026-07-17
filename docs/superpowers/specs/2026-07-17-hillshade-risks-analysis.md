# Hillshade: Raster-DEM Architectural Change — Devil's Advocate Risk Analysis

> Date: 2026-07-17  
> Branch: `feature/hillshade`  
> Scope: Proposed conversion from static preshaded PNGs to `raster-dem` + `hillshade` layers in maplibre-gl v6  

---

## 1. Executive Summary

**Verdict: ABANDON — or at minimum, significantly modify the scope. The proposal swaps two preshaded PNGs (137 KB, zero runtime shader cost) for a heavier tiling pipeline (3–7x larger, GPU cost per frame, untested PMTiles integration) without a commensurate visual or functional benefit.**

The current implementation works, is deterministic, and renders correctly. Converting to `raster-dem` adds:
- **Infrastructure risk:** no known working combination of `raster-dem` + PMTiles in maplibre-gl.
- **File-size bloat:** DEM tiles weigh significantly more than two global preshaded images.
- **Runtime cost:** per-tile GPU hillshade computation on every frame, and hidden interactions with the `terrain` subsystem.
- **Maintenance cost:** new tile-writing code in the tile compiler, new DEM-encoding logic, new frontend style wiring, and a new cache warmup strategy.

If we want better zoom-level detail, the correct next step is **multi-zoom pre-shaded image tiles served as a `raster` tile source or a single higher-resolution image**, not a full DEM retooling.

---

## 2. Capability Check — Does maplibre-gl v6 Support It?

### 2.1 `raster-dem` as a source type

The source code says `raster-dem` is **not** deprecated, experimental, or at risk. It is a first-class, actively maintained core source type.

- `src/source/source.ts` registers the source factory  
  `case 'raster-dem': return RasterDEMTileSource;`  
- `src/source/raster_dem_tile_source.ts` loads tiles via `ImageRequest.getImage()` and passes them to a worker for DEM decoding  
- `src/style/style_layer/hillshade_style_layer.ts` exposes full paint properties (`hillshade-exaggeration`, `hillshade-illumination-direction`, `hillshade-illumination-altitude`, `hillshade-method`, `hillshade-highlight-color`, `hillshade-shadow-color`, `hillshade-accent-color`, `hillshade-illumination-anchor`)  
- `src/webgl/draw/draw_hillshade.ts` and `src/shaders/glsl/hillshade*.glsl` render the layer in the offscreen pass and compose it in the translucent pass  

### 2.2 `hillshade` layer API in maplibre v6

The `hillshade` layer type supports **five** algorithms (source: `hillshade.fragment.glsl` and style spec `index.d.ts`):

| Method | Description |
|--------|-------------|
| `standard` | Legacy maplibre hillshade (cosine shading) |
| `basic` | GDAL `gdaldem hillshade` equivalent (black→white) |
| `igor` | GDAL `HillshadeIgorAlg` |
| `combined` | GDAL `HillshadeCombinedAlg` |
| `multidirectional` | Multiple independent light sources |

This provides **more expressive lighting** than the current fixed Java computation (az=315°, alt=45°). Users could adjust illumination direction or even intensity at runtime.

### 2.3 DEM encoding options

The tile compiler would need to encode raw elevation into one of three schemes (`src/data/dem_data.ts`):

| Encoding | Channel formula | Notes |
|----------|-----------------|-------|
| `mapbox` | (R × 6553.6 + G × 25.6 + B × 0.1) − 10000 | Default |
| `terrarium` | (R × 256 + G + B / 256) − 32768 | Open, simple |
| `custom` | (R × redFactor + G × greenFactor + B × blueFactor) − baseShift | For anything else |

`terrarium` is the most straightforward to write from Java, but `mapbox` is the default for historical reasons.

### 2.4 PMTiles compatibility

**This is the single biggest capability gap.**  
`RasterDEMTileSource.loadTile()` fetches binary tile data through the standard `ImageRequest.getImage()` path and expects to decode a PNG/WebP tile into elevation values. In theory, a `pmtiles://` URL registered via `addProtocol` can return any binary blob. The PMTiles library (`Protocol` class) serves tiles by raw bytes.

**However:**
- The `RasterTileSource` (parent class) relies on `url` or `tiles` arrays. A `tiles` entry like `['pmtiles://hillshade.pmtiles/{z}/{x}/{y}']` would in principle work.
- But there is **zero** evidence in the maplibre-gl test suite, examples, or documentation that `raster-dem` + PMTiles have ever been combined. The existing PMTiles integration in this codebase (`frontend/src/main.ts:36`) only registers the protocol for the `vector` source (`pmtiles://${HOST}/golarion.pmtiles`).
- The PMTiles library's own code path (`pmtiles/dist/esm/index.js`) handles tile requests but its `leafletRasterLayer` helper is entirely separate from the `tilev4` method used in maplibre.

**Risk level: HIGH.** If this combination fails silently (e.g., worker-side decoding crashes, or tiles are mis-interpreted), debugging occurs deep inside maplibre's worker thread image decoding pipeline.

### 2.5 Version status

The installed frontend package is `maplibre-gl ^6.0.0-20`. The `raster-dem` source type, `hillshade` layer type, and all associated rendering code are **stable, non-deprecated, and maintained** in the source tree. No deprecation warnings were found anywhere for these types.

---

## 3. Performance Analysis

### 3.1 GPU cost — per-frame hillshade computation

The `hillshade` layer uses a **two-pass GPU shader** (`draw_hillshade.ts`):
1. **Offscreen pass (`renderPass === 'offscreen'`):** The `hillshadePrepare` fragment shader reads the DEM texture, computes slope/aspect per pixel into an FBO, and stores partial derivatives in RG channels.
2. **Translucent pass (`renderPass === 'translucent'`):** The `hillshade` fragment shader reads the prepared FBO, applies illumination direction, exaggeration, and color mixing, then composites.

This shader cost is **per-tile, per-frame** (minus caching of the offscreen pass if the tile hasn't changed). For a viewport showing ~10 DEM tiles, that's 10 offscreen render-to-texture operations plus 10 compositing draws every frame.

With the current preshaded PNG approach, hillshade is **pre-computed offline**; at runtime it's just a single `raster` layer composite with linear interpolation. GPU cost is roughly **one texture fetch per pixel** plus standard raster blending—dramatically cheaper.

### 3.2 Download cost

Current state (two preshaded PNGs + world files):

```
hillshade-hills.png      75,591 bytes
hillshade-mountains.png  79,416 bytes
Total:                  ~155 KB (one-time download, cached forever)
```

Proposed DEM tile set (estimated for synthetic elevation over the same Golarion extent, 256×256 terrarium tiles at zoom 0–5, plus overzoom beyond that):

| Zoom | Approx. tiles covering Golarion extent | Est. size per tile | Cumulative subtotal |
|------|----------------------------------------|--------------------|--------------------|
| 0 | 1 | ~0.5 KB | ~0.5 KB |
| 1 | 1 | ~0.6 KB | ~1.1 KB |
| 2 | 3 | ~0.7 KB | ~3.2 KB |
| 3 | 7 | ~0.9 KB | ~9.5 KB |
| 4 | 28 | ~1.2 KB | ~43 KB |
| 5 | 112 | ~1.5 KB | ~211 KB |
| 6 | ~500 | ~2.0 KB | ~1.2 MB |
| 7 | ~2,000 | ~2.5 KB | ~6.2 MB |
| 8 | ~8,000 | ~3.0 KB | ~30 MB |

These are **order-of-magnitude estimates**. A synthetic DEM with large flat zero regions will compress very well. But even at z0–z5 only, the archive is **already 2× the size of the current approach**.

Maplibre `raster-dem` tiles are **not cached at rest by the browser like image tiles** — they are decoded into `DEMData` objects in workers and transferred back as typed arrays. The compressed tile bytes are discarded after decoding. This means:
- IndexedDB or ServiceWorker caching of the original binary would need to be **explicitly built**.
- The current `CachedSource` wrapping only works for the vector PMTiles archive. DEM tiles would need a separate caching strategy.
- If the browser evicts the in-memory tile cache, maplibre must re-fetch and re-decode the DEM tiles from PMTiles.

### 3.3 Memory cost

Each loaded DEM tile is stored as `DEMData` with a `Uint32Array` sized to `stride × stride`, where stride = tile dimension + 2 (for border padding). For 256×256 tiles, that's 258×258×4 bytes ≈ **266 KB per tile**. With 10 tiles in the viewport cache → **~2.6 MB**. Add the GPU textures (RGBA for the prepare FBO, same size) → **another ~2.6 MB**. This is memory that simply doesn't exist in the preshaded PNG approach.

### 3.4 Terrain interaction

**Critical finding from source code (`src/ui/map.ts:2353`):**

```ts
if (thisLayer.type === 'hillshade' && thisLayer.source === options.source) {
    warnOnce('You are using the same source for a hillshade layer and for 3D terrain. …');
}
```

This tells us two things:
1. **Hillshade does NOT require `terrain`**. A `hillshade` layer referencing a `raster-dem` source works independently of `map.setTerrain()`.
2. **But if `terrain` IS enabled on the same `raster-dem` source**, maplibre emits a warning and the rendering quality may degrade because the same texture is used for two different purposes.

However, `terrain` is NEVER enabled in the current codebase, and enabling it would be a **separate major visual decision** — it introduces 3D perspective, shifts labels to terrain surface, changes zoom behavior, and triggers `renderToTexture` mode (`painter.ts:512`). The `terrain` object (`src/render/terrain.ts`) creates framebuffers for depth and coords, constructs a 3D mesh, and alters the projection matrix. This would **fundamentally change how the map looks**, which is out of scope.

**Bottom line:** We could use `hillshade` without `terrain`, but the codebase's interaction with terrain is deep and well-tested. The risk of silent side effects (e.g., tile manager interactions, depth buffer assumptions) is non-zero.

---

## 4. Alternatives Considered

### 4.1 Multi-zoom pre-shaded tiles (raster tile source)

Instead of DEM + runtime hillshade, generate pre-shaded PNG tiles at multiple zoom levels and serve them through a standard `raster` source (not `image`).

| Pros | Cons |
|------|------|
| Zero GPU hillshade cost per frame | Still need a tile generation pipeline |
| Proven `raster` + `tiles` / `pmtiles` integration | Total tile count similar to DEM approach |
| Can use maplibre's built-in `raster-resampling` | Preshaded tiles are harder to adjust (light direction baked in) |
| Standard maplibre feature, well-documented | |

**Assessment:** The cleanest upgrade if we need zoom-level detail. It side-steps the DEM decoding and GPU shading pipeline entirely, while keeping the output visually identical to the current approach.

**Recommendation:** Keep the current image sources for now, but when zoom detail becomes an actual user complaint, switch to tiled `raster` sources of pre-shaded PNG tiles. This requires only a `COMPILE_TILES`-like step that writes raster tiles, no DEM encoding or terrain subsystem involvement.

### 4.2 Single higher-resolution image with overzoom

Maplibre's `image` source overzooms. The current PNGs are 4096×2048 (actually the current ones are at some resolution based on gridWidth/gridHeight hardcoded to 4096/2048 in GenerateHillshade). At a ~170° × 82° extent, that's ~24 pixels per degree. An 8192×4096 image would be ~48 pixels per degree.

| Pros | Cons |
|------|------|
| One file, one request | PNG size grows to ~250–400 KB per image |
| Zero tile-generation complexity | Still blurry at very high zoom |
| Works today, no code changes needed | Memory footprint of large image texture |

At zoom 6 (5.6° per tile, viewport ~10° wide), an 8192-wide image gives ~480 pixels covering the viewport. That's acceptable for styling.

**Recommendation:** If overzoom quality is the driving concern, simply **double the grid resolution** in `HillshadeGrid` and regenerate the PNGs. This is a one-line parameter change and takes the project from ~75 KB per image to ~250 KB — still well below the DEM tile set size at z0–z5. This is the most cost-effective improvement.

### 4.3 Use GDAL (gdal2tiles.py / rio-mbtiles) for DEM → tiles

If we must have DEM tiles, let GDAL do the tiling.

| Pros | Cons |
|------|------|
| GDAL is battle-tested, handles tile pyramids, encoding, compression | Introduces external binary dependency outside the current pure-Java/JavaScript pipeline |
| `gdaldem hillshade` and `gdal2tiles.py` are standard | Docker image change, CI change |
| Can produce terrarium or mapbox RGB PNGs directly | Loses the "pure Java" advantage of the current implementation |

**Assessment:** The current project deliberately avoids runtime GDAL calls in the tile compiler (the design note: "Grid computation entirely in Java using existing JTS dependency"). Introducing GDAL `gdal2tiles.py` would violate that constraint.

**Recommendation:** Only consider if we move away from the pure-Java constraint. Not worth it for hillshade alone.

### 4.4 Status quo (keep current preshaded `image` + `raster`)

| Pros | Cons |
|------|------|
| Works now, already committed | No per-zoom detail adjustment |
| 137 KB total, single download | Light direction fixed at az=315/alt=45 |
| Zero runtime GPU cost beyond standard raster composite | Cannot adjust hillshade parameters at runtime |
| Simplest frontend style (two `image` sources, two `raster` layers) | |

**Recommendation:** Unless zoom detail is shown to be a user-visible problem in testing, **do not change the architecture**.

---

## 5. Implementation Risk Matrix

| Risk | Likelihood | Impact | Mitigation | Residual Risk |
|------|------------|--------|------------|---------------|
| PMTiles + `raster-dem` tile fetching fails or silently produces garbled tiles | Medium–High | High | None known; would require upstream debugging | High |
| Tile compiler DEM tile generation bugs (encoding, border padding, tile alignment) | Medium | Medium | Extensive testing; but DEM encoding is finicky | Medium |
| GPU hillshade shader cost causes frame drops on low-end devices | Medium | Low–Medium | Only visible during pan/zoom; baked PNGs have no such cost | Low |
| Total download size exceeds acceptable budget (3–7x current) | High | Medium | Could limit to z0–z5, but then zoom detail suffers | Medium |
| Accidental terrain activation via shared source or future feature | Low | High | Maintain strict separation; document in style | Low |
| Maintenance burden of dual source types (`raster-dem` for hillshade, vector for everything else) | High | Medium | Ongoing cognitive load for developers | Medium |
| DEM tile cache eviction leading to re-fetch stutter | Medium | Low | Could add custom `addProtocol` caching, but that's more code | Low |
| JDK ImageIO cannot write terrarium/mapbox RGB PNGs correctly, needing custom writer | Medium | Medium | Write custom PNG encoder or switch to GDAL | Medium |

---

## 6. Final Recommendation

### 6.1 Do NOT convert to `raster-dem` + `hillshade`

The proposal is a **solution in search of a problem**. The current implementation:
- Is visually adequate (confirmed by the plan's own visual goals).
- Is trivially resizable for better overzoom (double grid resolution → ~250 KB per image).
- Has zero GPU runtime cost.
- Has zero PMTiles integration risk.
- Is already built and committed.

### 6.2 What to do instead

1. **Immediate (no code changes):** Tune `gridWidth` / `gridHeight` in `GenerateHillshade.java` from 4096×2048 to 8192×4096 if zoom-level 6+ detail looks poor. This doubles file size to ~250 KB per PNG but still totals under 600 KB.
2. **If zoom-specific granularity truly matters later:** Generate a pyramid of pre-shaded PNG tiles and serve them via a standard `raster` source with `{z}/{x}/{y}` URLs (or PMTiles for raster if/when that becomes well-tested). This keeps GPU cost at zero while adding zoom-level control.
3. **Only revisit DEM tiles if:** (a) we acquire real DEM data (e.g., a digital elevation model for Golarion), or (b) we decide to enable 3D terrain as a feature, at which point DEM tiles become architecturally justified.

### 6.3 If overruled: minimum viable scope

If the team insists on `raster-dem`, scope should be **strictly limited**:
- Limit tile pyramid to **zoom 0–5 only**.
- Use `terrarium` encoding (easiest to implement in Java).
- Generate a **separate PMTiles archive** `hillshade-dem.pmtiles` to avoid sharing a source with terrain.
- Use the `tiles` array with a `pmtiles://` URL, not `url` (TileJSON), since we have PMTiles infrastructure already.
- Register a **second** `addProtocol` handler or extend the existing `Protocol` with awareness of the DEM tile PMTiles archive.
- **Do not** add `terrain` to the style; do not call `map.setTerrain()`.
- Accept the download-size penalty (~300 KB for z0–z5).

Even then, the engineering effort (new tile-writer, DEM encoding, new source registration, testing PMTiles interop) dwarfs the benefit of adjustable light angle for a purely decorative hillshade layer.

---

*Sources: maplibre-gl v6 source code (`source.ts`, `raster_dem_tile_source.ts`, `dem_data.ts`, `hillshade_style_layer.ts`, `draw_hillshade.ts`, `terrain.ts`, `map.ts`, `painter.ts`, `tile_manager_raster_dem.ts`), MapLibre style spec type definitions (`@maplibre/maplibre-gl-style-spec/dist/index.d.ts`), PMTiles library (`pmtiles/dist/esm/index.js`), current project source (`HillshadeGrid.java`, `style.ts`, `main.ts`).*
