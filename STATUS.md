# Project Status

Generated: 2026-07-16

## Current Work: Mountain Hillshade Feature — IMPLEMENTATION COMPLETE

### Goal
Create all the code necessary to have a hillshade layer for mountains and a different one for hills in the maplibre map. The hillshade images should be generated automatically from their shapes. Mountain points from locations.json should be integrated into the mountain hillshade.

### Implementation Summary

#### Task 1: HillshadeGrid - Pure Java DEM Synthesis
- **File:** `tile-compiler/src/main/java/.../HillshadeGrid.java`
- **Status:** Implemented, unit-tested (3/3 tests pass)
- Synthetic DEM from 2D polygons using distance-to-boundary transforms
- Hillshade computed in pure Java (az=315deg, alt=45deg, z=1)
- Writes RGBA PNG + ESRI World File for georeferencing
- Deterministic: same inputs + fixed parameters lead to identical output
- Supports explicit world bounds (used for fixed extent across mountains/hills)
- Mountain point bumps: gaussian elevation addition for location points

#### Task 2: GenerateHillshade StepExecutor
- **File:** `tile-compiler/src/main/java/.../GenerateHillshade.java`
- **Status:** Implemented, compiles successfully
- Reads polygon GeoData and optional mountain locations
- Converts model Geometry to JTS Geometry for processing
- Emits bounds TypeScript files for frontend coordinates
- YAML fields: `layer`, `maxElevation`, `power`, `includeLocations`, `gridWidth/Height`, explicit bounds

#### Task 3: Pipeline Wiring (steps.yaml)
- **Status:** Implemented
- Two new groups added:
  - `hillshade-mountains`: mountains polygons + mountain locations, maxElev=255, power=0.7
  - `hillshade-hills`: hills polygons only, maxElev=128, power=1.0
- Both use explicit bounds: [-70, 42] to [100, -40] (covers all known data)
- Grid: 2048x1024 pixels

#### Task 4: Frontend Map Style (style.ts)
- **Status:** Implemented, compiles
- Two `image` sources with static coordinates matching the tile compiler bounds
- Two `raster` layers inserted after background, before geometry fill:
  - `hillshade-mountains`: opacity 0.35
  - `hillshade-hills`: opacity 0.25

#### Task 5: Visual Output Generated
- `hillshade-mountains.png` (78K) - 236 polygons + 42 location point bumps
- `hillshade-hills.png` (74K) - 431 polygons
- Both with world files for georeferencing

### Testing Status

| Test | Result |
|------|--------|
| HillshadeGrid unit tests | PASS 3/3 |
| HillshadeGrid integration test with real GeoPackage data | PASS 2/2 |
| Tile compiler compilation | PASS |
| GenerateHillshade compilation | PASS |
| Generated PNGs from real data | PASS (both files ~78KB/74KB) |
| Frontend TypeScript compilation | Pre-existing maplibre-gl version mismatch; hillshade additions have no TS errors |
| Map rendering in browser | Not tested - requires frontend build |

### Known Issues / Next Steps

1. **Frontend build**: Pre-existing `maplibre-gl` version mismatch (5.24.0 vs ^6.0.0-20) blocks `npm run build`. Run `cd frontend && npm update maplibre-gl`.

2. **Visual tuning**: Opacities and elevation parameters may need iteration based on aesthetic review:
   - Mountains: `maxElevation=255`, `power=0.7`, `opacity=0.35`
   - Hills: `maxElevation=128`, `power=1.0`, `opacity=0.25`

3. **Docker pipeline**: For the canonical production build environment, run inside the QGIS Docker image (all tools available there).

### Files Changed

- `tile-compiler/src/.../HillshadeGrid.java` - New
- `tile-compiler/src/.../GenerateHillshade.java` - New
- `tile-compiler/src/test/.../HillshadeGridTest.java` - New
- `tile-compiler/src/test/.../HillshadeIntegrationTest.java` - New
- `tile-compiler/src/test/.../HillshadeWritePngs.java` - New (utility)
- `tile-compiler/steps.yaml` - Modified
- `frontend/src/ml-style/style.ts` - Modified
- `.gitignore` - Modified (ignore generated hillshade PNGs/WLDs)
- `docs/superpowers/specs/2026-07-16-hillshade-design.md` - New
- `docs/superpowers/plans/2026-07-16-hillshade-plan.md` - New

### Commits (branch: feature/hillshade)

1. `9ff2fc5` - Add hillshade design spec and implementation plan
2. `670f0fa` - feat(tile-compiler): add GenerateHillshade StepExecutor
3. `a9e69a1` - feat(frontend): add hillshade raster layers
4. `8b51c92` - fix(frontend): move hillshade layers after opaque geometry fill
5. `b5ad462` - test: add integration tests and hillshade PNG writer
