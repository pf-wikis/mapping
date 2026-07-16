# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **PathfinderWiki Golarion Map** — an interactive map of the Pathfinder RPG setting of Golarion. It consists of:

- a **Java vector tile compiler** (`tile-compiler/`) that generates a PMTiles file from geospatial data
- a **TypeScript frontend** (`frontend/`) rendering the tiles with maplibre-gl-js
- a **Java wiki-downloader bot** (`wiki-downloader/`) that scrapes city/POI coordinates from PathfinderWiki articles

Live site: https://map.pathfinderwiki.com

## Project Structure

```
build/                  # Docker image + build scripts for CI
tile-compiler/          # Java (JDK 25, Maven) tile generator
  src/main/java/...     # Layer compilation DAG, geospatial processing, tile export
  steps.yaml            # Declarative DAG defining all compilation steps
  run.sh                # Convenience build+run script
frontend/               # TypeScript + Vite frontend
  src/main.ts           # Entry point — map initialization, control registration
  src/ml-style/style.ts # Map style definition (layers, colors, filters)
  src/ml-style/state.ts # Global state properties (labels, borders, time, etc.)
  src/tools/            # Custom map controls (search, time slider, measure, etc.)
  gen/                  # Generated TypeScript files from tile-compiler
  public/               # Output directory for PMTiles + sprites + fonts
wiki-downloader/        # Java bot for scraping wiki coordinates
sources/                # QGIS project file + GeoJSON cities/locations
```

## Build & Development Commands

### Prerequisites
- **Java JDK 25+** and Maven
- **Node.js >= 23.8.0** and npm
- [felt/tippecanoe](https://github.com/felt/tippecanoe) (tile generation)
- [Kart](https://kartproject.org/) (for the separate `mapping-data` repo)
- GDAL, GRASS, QGIS (for geometry processing)
- npm globals: `mapshaper`, `geojson-polygon-labels`, `curve-interpolator@3.0.1`, `@indoorequal/spritezero-cli`

### Tile Compiler (tile-compiler/)
```bash
cd tile-compiler
mvn compile package
java -jar target/tile-compiler.jar
# or simply:
./run.sh
```

The compiler reads from `../../mapping-data/mapping-data.gpkg` (GeoPackage) and `../sources/*.geojson`, executes the DAG defined in `steps.yaml`, and writes:
- `../frontend/public/golarion.pmtiles` — the vector tile archive
- `../frontend/gen/*` — generated TypeScript metadata (layer props, time slices, highlights)

To test: `mvn -B compile test`

### Frontend (frontend/)
```bash
cd frontend
npm i
npm start           # Vite dev server (default port 5173)
npm run build       # Production build to dist/
```

The dev server proxies `/sprites` and `/search.json` from production so remote data can be used without local tiles. No unit tests exist for the frontend.

### Wiki Downloader (wiki-downloader/)
```bash
cd wiki-downloader
mvn compile package
# Execute main class to update sources/cities.geojson and sources/locations.geojson
```

## High-Level Architecture

### Tile Compilation Pipeline

The tile-compiler is the most complex component. It uses a **declarative DAG** (`steps.yaml`) processed by `LayersCompiler` using the [Dexecutor](https://github.com/dexecutor/dexecutor) library with virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`).

**Key architectural concepts:**

1. **Steps & Executors**: Each entry in `steps.yaml` defines a group of sequential steps. Each step maps to a Java class extending `StepExecutor` (under `layercompiler.steps.*`). Steps declare dependencies on other groups, and the compiler builds a directed graph, executing independent branches in parallel.

2. **Content Model**: Step outputs are wrapped in a `Content` abstraction that supports both merged (timeless) and time-sliced data. The `Time.Requirement` annotation on step executors controls whether inputs are merged or sliced. Time-sliced content lets the same geometry exist in different historical eras.

3. **Processing Steps** (most common types):
   - `READ_FILE` — loads GeoPackage layers or GeoJSON files
   - `ADD_FRACTAL_DETAIL` — adds coastline/edge fractalization via noise
   - `SMOOTH_LINES` / `SHAPE_RIVERS` — spline interpolation and tapering
   - `RESOLVE_LABELS` / `GENERATE_LABEL_CENTERS` — label placement and center point generation
   - `MERGE_GEOMETRY` / `MERGE_BORDERS` / `MERGE_LABELS` — combines related layers
   - `PREPARE_EXPORT` — finalizes GeoJSON for Tippecanoe ingestion
   - `COMPILE_TILES` — calls Tippecanoe to produce PMTiles
   - `COMPILE_SPRITES` — generates map icon spritesheets
   - `CREATE_SEARCH_INDEX` — builds the location search index
   - `TIME_META_*` — manages temporal data for the time slider

4. **Execution Plan Generation**: `ExecutionPlan.parseFromFile()` reads `steps.yaml`, builds a Guava `MutableNetwork<StepDescription, Edge>`, resolves intra-group sequential dependencies and cross-group named dependencies, then registers the graph with Dexecutor.

### Frontend Map Style

The map style is **TypeScript-code-defined** in `frontend/src/ml-style/style.ts`, not a static JSON file. A custom Vite plugin (`compile-style` in `vite.config.ts`) compiles it at build time into a virtual module `virtual:style`, which is imported by `main.ts`. The style is validated against the maplibre style spec during compilation.

**Key frontend concepts:**

1. **Layer Generation**: The `createLayer()` helper in `style.ts` generates map layers from tile layer names (e.g., `geometry`, `borders`, `rivers`, `labels`, `locations`). It automatically applies zoom filters, time filters, and min/max zoom bounds based on generated metadata (`props-meta-golarion.ts`).

2. **Global State**: `frontend/src/ml-style/state.ts` defines global style-state properties (`timeIndex`, `rotated`, `showLabels`, `showLocations`, `showBorders`). These are referenced in style expressions as `['global-state', 'propName']` and updated at runtime via `GolarionMap.setState()`.

3. **Time Slider**: The map supports temporal viewing — borders, labels, and locations can appear/disappear based on historical time ranges. The `TimeSliderControl` lets users scrub through eras. Time data is compiled from the mapping-data repository's temporal attributes.

4. **URL-Driven Options**: `URLOptions.ts` parses query parameters for initial map state (location, zoom, time, embedded mode, debug). The `GolarionMap` class wraps the `maplibre-gl` `Map` instance and provides lifecycle helpers.

5. **Custom Controls**: Located in `src/tools/` — search (`SearchControl`), measurement (`MeasureControl`), projection toggle (`ProjectionControl`), compact attribution (`CompactAttributionControl`), right-click context menu, location popups, and more.

6. **PMTiles Caching**: A custom `CachedSource` wraps the PMTiles fetch to use IndexedDB for offline tile caching in the browser.

### Data Flow

1. **Source data**: `mapping-data` (separate Kart repo → GeoPackage) + `sources/cities.geojson` + `sources/locations.geojson`
2. **tile-compiler** processes via DAG → outputs `frontend/public/golarion.pmtiles`, `frontend/public/sprites/`, `frontend/public/search.json`, and `frontend/gen/*.ts`
3. **frontend** Vite build compiles style + bundles JS → `frontend/dist/`

### CI/CD

GitHub Actions workflows:
- `tile-compiler-build.yml` — builds and tests the Java tile compiler on `tile-compiler/**` changes
- `frontend-build.yml` — builds the frontend on `frontend/**` changes
- `map-deploy.yml` — on push to `main` (or `repository_dispatch` from mapping-data), SSHs to the production server to pull, build Docker image, and deploy

## Important Notes

- The `mapping-data` repository is managed by **Kart** (not git) and must be cloned separately using `kart clone` into a sibling `mapping-data/` directory.
- `frontend/gen/` and `frontend/public/golarion.pmtiles` are generated files — do not edit them directly.
- `sources/cities.geojson` and `sources/locations.geojson` are auto-generated by the wiki-downloader bot. **Do not edit them manually** — coordinates are sourced from PathfinderWiki articles.
- If the `GENERATE_LABEL_CENTERS` step fails with QGIS UI errors, set `QT_QPA_PLATFORM=offscreen` to force headless mode.
- The tile-compiler supports a `-prodDetail` flag for higher detail and `-maxZoom 12` for production tiles.
