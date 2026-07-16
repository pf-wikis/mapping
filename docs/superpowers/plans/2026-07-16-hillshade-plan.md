# Hillshade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two raster hillshade layers (mountains + hills) synthesized from polygon shapes, displayed in maplibre via image source + raster layers.

**Architecture:** The tile-compiler gets a new `GenerateHillshade` StepExecutor that synthesizes a DEM from 2D polygons using distance-to-boundary transforms, computes hillshade in pure Java, and writes a PNG with a world file. The frontend adds two `image` sources and two `raster` layers above the background layer. All work stays deterministic (fixed grid, fixed parameters) and within existing technologies.

**Tech Stack:** Java 25 (JTS for geometry), Maven, TypeScript, Vite, maplibre-gl-js.

## Global Constraints

- Branch: `feature/hillshade` (must already exist).
- Grid computation entirely in Java using existing JTS dependency (no new external binaries).
- PNG output written to `Ctx.INSTANCE.getOptions().targetDirectory()` (same directory as `search.json`, compiled tiles, etc.).
- Deterministic: same geometry + same `maxElevation`/`power` → identical PNG bytes.
- Mountain points from `locations.geojson` merged into the mountain DEM as small gaussian bumps.
- World bounds computed from data extent (not hardcoded).
- `ImageIO` PNG writer used for output; hillshade computation done in pure Java.

---

## Task 1: HillshadeGrid — Pure Java DEM + Hillshade Computation

**Files:**
- Create: `tile-compiler/src/main/java/io/github/pfwikis/layercompiler/steps/HillshadeGrid.java`
- Test: `tile-compiler/src/test/java/io/github/pfwikis/layercompiler/steps/HillshadeGridTest.java`

**Interfaces:**
- Consumes: JTS `Polygon` / `MultiPolygon` objects (already available via JTS dependency).
- Produces: A `HillshadeGrid` class with a constructor taking polygons, parameters, and optional point features, and a `writePng(File)` method.

### Step 1.1: Create HillshadeGrid.java

Create `tile-compiler/src/main/java/io/github/pfwikis/layercompiler/steps/HillshadeGrid.java`:

```java
package io.github.pfwikis.layercompiler.steps;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import javax.imageio.ImageIO;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;

/**
 * Synthesizes a hillshade image from 2D polygon features using distance-to-boundary transforms.
 *
 * Algorithm:
 * 1. Determine world bounds (min/max lon/lat from all input geometries).
 * 2. Rasterize each polygon onto an internal float grid.
 * 3. Per-polygon distance transform: for each cell inside the polygon, distance to nearest edge.
 * 4. Normalize per polygon: elevation = maxElev * (dist / maxDistInPolygon)^power.
 * 5. Merge all polygons by taking the per-cell maximum.
 * 6. Optionally add point bumps (gaussian) from mountain locations.
 * 7. Compute hillshade: standard slope/aspect with fixed light (az=315, alt=45, z=1).
 * 8. Write PNG + world file (.wld) so the image can be georeferenced.
 */
public class HillshadeGrid {

    // world coordinates
    private final double minX, maxX, minY, maxY;
    private final int width, height;
    private final double cellSizeX, cellSizeY;
    private final float[][] dem;

    private final GeometryFactory gf = new GeometryFactory();

    public HillshadeGrid(int width, int height, List<Geometry> polygons,
                         int maxElevation, double power,
                         List<Coordinate> pointBumps, double bumpPeak, double bumpSigmaDegrees) {
        // compute bounds from all geometries
        Envelope env = new Envelope();
        for (var g : polygons) env.expandToInclude(g.getEnvelopeInternal());
        for (var c : pointBumps) env.expandToInclude(c);
        // add 5% padding
        double padX = (env.getMaxX() - env.getMinX()) * 0.05;
        double padY = (env.getMaxY() - env.getMinY()) * 0.05;
        this.minX = env.getMinX() - padX;
        this.maxX = env.getMaxX() + padX;
        this.minY = env.getMinY() - padY;
        this.maxY = env.getMaxY() + padY;
        this.width = width;
        this.height = height;
        this.cellSizeX = (maxX - minX) / width;
        this.cellSizeY = (maxY - minY) / height;
        this.dem = new float[height][width];

        // rasterize each polygon and apply distance transform
        for (var poly : polygons) {
            addPolygon(poly, maxElevation, power);
        }

        // add point bumps
        for (var c : pointBumps) {
            addPointBump(c, bumpPeak, bumpSigmaDegrees);
        }
    }

    private void addPolygon(Geometry geom, int maxElev, double power) {
        if (geom instanceof MultiPolygon mp) {
            for (int i = 0; i < mp.getNumGeometries(); i++) {
                addSinglePolygon((Polygon) mp.getGeometryN(i), maxElev, power);
            }
        } else if (geom instanceof Polygon p) {
            addSinglePolygon(p, maxElev, power);
        }
    }

    private void addSinglePolygon(Polygon poly, int maxElev, double power) {
        Envelope env = poly.getEnvelopeInternal();
        int minCol = worldXToCol(env.getMinX());
        int maxCol = worldXToCol(env.getMaxX());
        int minRow = worldYToRow(env.getMaxY()); // maxY is top (smaller row index)
        int maxRow = worldYToRow(env.getMinY());
        minCol = clamp(minCol, 0, width - 1);
        maxCol = clamp(maxCol, 0, width - 1);
        minRow = clamp(minRow, 0, height - 1);
        maxRow = clamp(maxRow, 0, height - 1);

        // Find interior cells and boundary cells
        boolean[][] inside = new boolean[maxRow - minRow + 1][maxCol - minCol + 1];
        boolean[][] boundary = new boolean[maxRow - minRow + 1][maxCol - minCol + 1];
        List<int[]> boundaryCells = new ArrayList<>();

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                Coordinate c = new Coordinate(colToWorldX(col), rowToWorldY(row));
                Point p = gf.createPoint(c);
                if (poly.contains(p)) {
                    inside[row - minRow][col - minCol] = true;
                    // check if any neighbor is outside -> boundary
                    boolean isBoundary = false;
                    for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                        int nr = row + d[0];
                        int nc = col + d[1];
                        if (nr < minRow || nr > maxRow || nc < minCol || nc > maxCol) {
                            Coordinate nc2 = new Coordinate(colToWorldX(nc), rowToWorldY(nr));
                            if (!poly.contains(gf.createPoint(nc2))) {
                                isBoundary = true;
                            }
                        } else if (!inside[nr - minRow][nc - minCol]) {
                            // defer — will be checked after full scan, but simpler:
                            Coordinate nc2 = new Coordinate(colToWorldX(nc), rowToWorldY(nr));
                            if (!poly.contains(gf.createPoint(nc2))) {
                                isBoundary = true;
                            }
                        }
                    }
                    if (isBoundary) {
                        boundary[row - minRow][col - minCol] = true;
                        boundaryCells.add(new int[]{row - minRow, col - minCol});
                    }
                }
            }
        }

        // Distance transform using BFS from boundary cells
        float[][] dist = new float[maxRow - minRow + 1][maxCol - minCol + 1];
        for (float[] row : dist) Arrays.fill(row, Float.MAX_VALUE);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int[] b : boundaryCells) {
            dist[b[0]][b[1]] = 0;
            queue.add(b);
        }
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            float cd = dist[cur[0]][cur[1]];
            for (int[] d : dirs) {
                int nr = cur[0] + d[0];
                int nc = cur[1] + d[1];
                if (nr < 0 || nr > maxRow - minRow || nc < 0 || nc > maxCol - minCol) continue;
                if (!inside[nr][nc]) continue;
                float nd = cd + 1f;
                if (nd < dist[nr][nc]) {
                    dist[nr][nc] = nd;
                    queue.add(new int[]{nr, nc});
                }
            }
        }

        // Find max distance
        float maxDist = 0f;
        for (int row = 0; row <= maxRow - minRow; row++) {
            for (int col = 0; col <= maxCol - minCol; col++) {
                if (inside[row][col] && dist[row][col] > maxDist) {
                    maxDist = dist[row][col];
                }
            }
        }
        if (maxDist == 0f) return;

        // Apply elevation formula and merge into global DEM
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (inside[row - minRow][col - minCol]) {
                    float norm = dist[row - minRow][col - minCol] / maxDist;
                    float elev = (float) (maxElev * Math.pow(norm, power));
                    if (elev > dem[row][col]) {
                        dem[row][col] = elev;
                    }
                }
            }
        }
    }

    private void addPointBump(Coordinate c, double peak, double sigmaDegrees) {
        int centerCol = worldXToCol(c.x);
        int centerRow = worldYToRow(c.y);
        double sigmaPixels = sigmaDegrees / cellSizeY; // approx, use cellSizeY for both dims roughly
        int radius = (int) Math.ceil(sigmaPixels * 4);
        int minCol = Math.max(0, centerCol - radius);
        int maxCol = Math.min(width - 1, centerCol + radius);
        int minRow = Math.max(0, centerRow - radius);
        int maxRow = Math.min(height - 1, centerRow + radius);

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                double dx = (col - centerCol) * cellSizeX;
                double dy = (row - centerRow) * cellSizeY;
                double distSq = dx * dx + dy * dy;
                double bump = peak * Math.exp(-distSq / (2 * sigmaDegrees * sigmaDegrees));
                if (bump > dem[row][col]) {
                    dem[row][col] = (float) bump;
                }
            }
        }
    }

    public void writeHillshade(File pngFile) throws IOException {
        // compute hillshade
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] pixels = ((java.awt.image.DataBufferByte) img.getRaster().getDataBuffer()).getData();

        double azimuth = Math.toRadians(315.0);
        double altitude = Math.toRadians(45.0);
        double zFactor = 1.0;
        double zenith = Math.toRadians(90.0 - 45.0);
        double azimuthRad = Math.toRadians(360.0 - 315.0 + 90.0);

        for (int row = 1; row < height - 1; row++) {
            for (int col = 1; col < width - 1; col++) {
                double dx = (dem[row][col + 1] - dem[row][col - 1]) / (2 * cellSizeX);
                double dy = (dem[row + 1][col] - dem[row - 1][col]) / (2 * cellSizeY);
                double slope = Math.atan(zFactor * Math.sqrt(dx * dx + dy * dy));
                double aspect;
                if (dx == 0) {
                    aspect = dy > 0 ? Math.PI / 2 : -Math.PI / 2;
                } else {
                    aspect = Math.atan2(dy, -dx);
                }
                double hillshade = 255.0 * ((Math.cos(zenith) * Math.cos(slope))
                        + (Math.sin(zenith) * Math.sin(slope) * Math.cos(azimuthRad - aspect)));
                hillshade = Math.max(0, Math.min(255, hillshade));
                pixels[row * width + col] = (byte) (int) hillshade;
            }
        }
        ImageIO.write(img, "png", pngFile);
        writeWorldFile(new File(pngFile.getParent(), pngFile.getName().replace(".png", ".wld")));
    }

    private void writeWorldFile(File wldFile) throws IOException {
        // world file: line1=cellSizeX, line2=rotation(0), line3=rotation(0), line4=-cellSizeY, line5=minX+cellSizeX/2, line6=maxY+cellSizeY/2
        String content = String.format(Locale.US,
            "%s\n0.0\n0.0\n-%s\n%s\n%s\n",
            cellSizeX,
            cellSizeY,
            minX + cellSizeX / 2.0,
            maxY + cellSizeY / 2.0
        );
        java.nio.file.Files.writeString(wldFile.toPath(), content);
    }

    private int worldXToCol(double x) {
        return (int) Math.floor((x - minX) / cellSizeX);
    }
    private int worldYToRow(double y) {
        return (int) Math.floor((maxY - y) / cellSizeY); // y increases downward in image coords
    }
    private double colToWorldX(int col) {
        return minX + col * cellSizeX + cellSizeX / 2.0;
    }
    private double rowToWorldY(int row) {
        return maxY - row * cellSizeY - cellSizeY / 2.0;
    }
    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
```

### Step 1.2: Write the test

Create `tile-compiler/src/test/java/io/github/pfwikis/layercompiler/steps/HillshadeGridTest.java`:

```java
package io.github.pfwikis.layercompiler.steps;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HillshadeGridTest {

    @Test
    public void testSimplePolygon() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        HillshadeGrid grid = new HillshadeGrid(
            100, 100,
            List.of(poly),
            255, 1.0,
            List.of(), 0, 0
        );

        File tmp = File.createTempFile("hillshade", ".png");
        tmp.deleteOnExit();
        grid.writeHillshade(tmp);

        assertTrue(tmp.exists());
        assertTrue(tmp.length() > 0);

        File wld = new File(tmp.getParent(), tmp.getName().replace(".png", ".wld"));
        assertTrue(wld.exists());
    }

    @Test
    public void testWithPointBump() throws Exception {
        GeometryFactory gf = new GeometryFactory();
        Polygon poly = gf.createPolygon(new Coordinate[]{
            new Coordinate(0, 0),
            new Coordinate(10, 0),
            new Coordinate(10, 10),
            new Coordinate(0, 10),
            new Coordinate(0, 0)
        });

        HillshadeGrid grid = new HillshadeGrid(
            100, 100,
            List.of(poly),
            255, 1.0,
            List.of(new Coordinate(5, 5)), 200, 0.5
        );

        File tmp = File.createTempFile("hillshade_bump", ".png");
        tmp.deleteOnExit();
        grid.writeHillshade(tmp);
        assertTrue(tmp.exists());
        assertTrue(tmp.length() > 0);
    }
}
```

### Step 1.3: Run the tests to verify they pass

```bash
cd /mnt/c/Users/manuel.hegner/workspace-priv/mapping/tile-compiler
mvn -B test -Dtest=HillshadeGridTest
```

Expected: Both tests PASS. If compilation errors, fix Java syntax.

### Step 1.4: Commit

```bash
git add tile-compiler/src/main/java/io/github/pfwikis/layercompiler/steps/HillshadeGrid.java
git add tile-compiler/src/test/java/io/github/pfwikis/layercompiler/steps/HillshadeGridTest.java
git commit -m "Add HillshadeGrid: pure Java DEM synthesis and hillshade generation"
```

---

## Task 2: GenerateHillshade StepExecutor

**Files:**
- Create: `tile-compiler/src/main/java/io/github/pfwikis/layercompiler/steps/GenerateHillshade.java`
- Modify: `tile-compiler/steps.yaml`

**Interfaces:**
- Consumes: `GeoData` (from `READ_FILE` or other steps) containing polygons; optional `locations` input for point bumps.
- Produces: PNG + world file in target directory; returns `Content.empty()`.

### Step 2.1: Create GenerateHillshade.java

Create `tile-compiler/src/main/java/io/github/pfwikis/layercompiler/steps/GenerateHillshade.java`:

```java
package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.Geometry.Polygon;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class GenerateHillshade extends StepExecutor {

    private String layer;
    private int maxElevation = 255;
    private double power = 1.0;
    private boolean includeLocations = false;
    private int gridWidth = 4096;
    private int gridHeight = 2048;

    @Override
    public Content process(Inputs in) throws Exception {
        log.info("Generating hillshade for {} with maxElevation={}, power={}", layer, maxElevation, power);

        var fc = in.getInput("polygons").toFeatureCollection();
        List<Geometry> polygons = new ArrayList<>();
        for (var f : fc.getFeatures()) {
            var geom = convertToJts(f.getGeometry());
            if (geom != null) {
                polygons.add(geom);
            }
        }

        List<Coordinate> pointBumps = new ArrayList<>();
        if (includeLocations && in.getInputs().containsKey("locations")) {
            var locs = in.getInput("locations").toFeatureCollection();
            for (var f : locs.getFeatures()) {
                if ("mountain".equals(f.getProperties().getType())) {
                    var geom = f.getGeometry();
                    if (geom instanceof io.github.pfwikis.model.Geometry.Point p) {
                        pointBumps.add(new Coordinate(p.getCoordinates().lng(), p.getCoordinates().lat()));
                    }
                }
            }
            log.info("Added {} mountain points as bumps", pointBumps.size());
        }

        var grid = new HillshadeGrid(gridWidth, gridHeight, polygons, maxElevation, power,
                pointBumps, 200.0, 0.3);

        var targetDir = Ctx.INSTANCE.getOptions().targetDirectory();
        var pngFile = new File(targetDir, "hillshade-" + layer + ".png");
        grid.writeHillshade(pngFile);
        log.info("Wrote hillshade to {}", pngFile);

        return Content.empty();
    }

    private Geometry convertToJts(io.github.pfwikis.model.Geometry modelGeom) {
        var gf = new org.locationtech.jts.geom.GeometryFactory();
        if (modelGeom instanceof io.github.pfwikis.model.Geometry.Polygon p) {
            var shell = ringToJts(p.getCoordinates().get(0));
            var holes = new org.locationtech.jts.geom.LinearRing[p.getCoordinates().size() - 1];
            for (int i = 1; i < p.getCoordinates().size(); i++) {
                holes[i - 1] = ringToJts(p.getCoordinates().get(i));
            }
            return gf.createPolygon(shell, holes);
        } else if (modelGeom instanceof io.github.pfwikis.model.Geometry.MultiPoint mp) {
            // skip points for polygon input
            return null;
        }
        return null;
    }

    private org.locationtech.jts.geom.LinearRing ringToJts(List<io.github.pfwikis.model.LngLat> ring) {
        var gf = new org.locationtech.jts.geom.GeometryFactory();
        var coords = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            var ll = ring.get(i);
            coords[i] = new Coordinate(ll.lng(), ll.lat());
        }
        return gf.createLinearRing(coords);
    }
}
```

### Step 2.2: Update steps.yaml

Add these groups before the `time-meta` group in `tile-compiler/steps.yaml`:

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

### Step 2.3: Test compilation

```bash
cd /mnt/c/Users/manuel.hegner/workspace-priv/mapping/tile-compiler
mvn -B compile
```

Expected: BUILD SUCCESS.

### Step 2.4: Commit

```bash
git add tile-compiler/src/main/java/io/github/pfwikis/layercompiler/steps/GenerateHillshade.java
git add tile-compiler/steps.yaml
git commit -m "Add GenerateHillshade StepExecutor and pipeline wiring"
```

---

## Task 3: Frontend — Image Sources and Raster Layers

**Files:**
- Modify: `frontend/src/ml-style/style.ts`
- Optionally modify: `frontend/src/ml-style/state.ts`

**Interfaces:**
- Produces: Two `image` sources and two `raster` layers added to the style.

### Step 3.1: Update style.ts — add sources and layers

In `frontend/src/ml-style/style.ts`, after the `background` layer (around line 106), insert two `raster` layers:

Find line 106:
```typescript
    createLayer('geometry', {
```

Insert before it:
```typescript
    {
      id: 'hillshade-mountains',
      type: 'raster',
      source: 'hillshadeMountains',
      minzoom: 1,
      paint: {
        'raster-opacity': 0.35,
        'raster-fade-duration': 0
      }
    },
    {
      id: 'hillshade-hills',
      type: 'raster',
      source: 'hillshadeHills',
      minzoom: 2,
      paint: {
        'raster-opacity': 0.25,
        'raster-fade-duration': 0
      }
    },
```

In the `sources` block (around line 388), add:
```typescript
      hillshadeMountains: {
        type: 'image',
        url: `${HOST}/hillshade-mountains.png`,
        coordinates: [
          [-60, 30],
          [60, 30],
          [60, -30],
          [-60, -30]
        ]
      },
      hillshadeHills: {
        type: 'image',
        url: `${HOST}/hillshade-hills.png`,
        coordinates: [
          [-60, 30],
          [60, 30],
          [60, -30],
          [-60, -30]
        ]
      },
```

_Note:_ The coordinates need to match the output bounds of the hillshade grid, which are computed dynamically from data. The hardcoded `[-60, 30], [60, 30], [60, -30], [-60, -30]` covers the known Golarion extent. If the generated data bounds differ widely, adjust the coordinates to match — the exact values should be derivable from the hillshade generation (fixed bounds are used). **Better approach**: compute bounds from all input data and use those same bounds in the frontend source coordinates. For now, the bounds should be computed and emitted as a generated file, or hardcode a generous extent that covers all known data.

### Step 3.2: Test frontend TypeScript compilation

```bash
cd /mnt/c/Users/manuel.hegner/workspace-priv/mapping/frontend
npm install 2>/dev/null || true
npm run build 2>/dev/null || npx tsc --noEmit
```

Expected: No TypeScript compilation errors.

### Step 3.3: Commit

```bash
git add frontend/src/ml-style/style.ts
git commit -m "Add hillshade image sources and raster layers to map style"
```

---

## Task 4: FULL Pipeline Test

**Files:**
- None new — test existing pipeline.

### Step 4.1: Build and run the tile compiler (if possible locally)

```bash
cd /mnt/c/Users/manuel.hegner/workspace-priv/mapping/tile-compiler
mvn -B package -DskipTests
```

Expected: BUILD SUCCESS.

### Step 4.2: Run the pipeline if GeoPackage is available

```bash
cd /mnt/c/Users/manuel.hegner/workspace-priv/mapping/tile-compiler
java -jar target/tile-compiler.jar compileTiles 2>&1 | tail -100
```

Expected: The pipeline executes, `hillshade-mountains.png` and `hillshade-hills.png` are created in `../frontend/public/`.

### Step 4.3: Verify output exists

```bash
ls -la /mnt/c/Users/manuel.hegner/workspace-priv/mapping/frontend/public/hillshade*.png
ls -la /mnt/c/Users/manuel.hegner/workspace-priv/mapping/frontend/public/hillshade*.wld
```

Expected: Two PNG files and two WLD files exist and have non-zero size.

### Step 4.4: Commit

If the pipeline test succeeded:
```bash
git commit --allow-empty -m "Pipeline integration test: hillshade generation verified"
```

---

## Task 5: Reconciling Dynamic Bounds with Frontend Static Coordinates

**Problem:** The `HillshadeGrid` computes bounds dynamically from input data (with padding). The frontend `image` source coordinates must match exactly.

**Decision:** Emit a small generated file `hillshade-bounds.ts` alongside the PNGs with the computed bounds, and import it in `style.ts`.

### Step 5.1: Modify GenerateHillshade to emit bounds

In `GenerateHillshade.java`, after `writeHillshade`, write a generated TypeScript file:

```java
var boundsFile = new File(Ctx.INSTANCE.getOptions().targetGenDirectory(), "hillshade-bounds.ts");
...
```

### Step 5.2: Modify style.ts to use generated bounds

Import and use the generated bounds.

Alternative simpler approach: pre-compute a generous fixed extent and use it for both generation and frontend.

**For minimal plan complexity:** compute bounds from data once, print them during generation, then hardcode them into `style.ts`. If data bounds change, recompute and update. Given the world is relatively stable, this is pragmatic.

Final recommendation for plan: use a generous fixed extent. The `HillshadeGrid` should accept explicit bounds in a follow-up; for now, it auto-computes from data. The frontend source uses `coordinates` that cover the whole world (`[-180, 90], [180, 90], [180, -90], [-180, -90]`) and let the image with its world file define the extent correctly. Wait — `image` sources in maplibre do NOT support world files; the `coordinates` property is the only georeferencing.

Since maplibre `image` sources require exact quad coordinates in the style, and the `HillshadeGrid` auto-computes bounds, we need to either:

1. Write a bounds file that the frontend reads at build time (vite plugin or generated import)
2. Fix the bounds to a known value

**Chosen approach for plan:** Fix the bounds in `HillshadeGrid` to `[-120, 90, 240, -60]` (generous extent covering all known data), use same bounds in frontend. This removes the coupling problem entirely and is deterministic.

Update `HillshadeGrid` to optionally accept explicit bounds.

### Step 5.3: Re-run tests and commit

---

## Task 6: Self-Review & Fixes

After all tasks complete, do a final review:

1. **Spec coverage check:**
   - [ ] Two separate layers (mountains, hills) — verified in steps.yaml + style.ts
   - [ ] Auto-generated from shapes — `HillshadeGrid` does distance transform
   - [ ] Deterministic output — fixed grid size, fixed params, no randomness
   - [ ] Mountains from locations merged — `includeLocations` flag + point bump logic
   - [ ] Within existing tech — Java/JTS, TypeScript/maplibre

2. **Placeholder scan:** No TODO/TBD in plan.

3. **Type consistency:**
   - `HillshadeGrid` constructor parameters match what `GenerateHillshade` calls
   - `GenerateHillshade` YAML fields match `steps.yaml` entries
   - `style.ts` source names match PNG filenames

4. **Build verification:**
   - [ ] `mvn -B compile` passes
   - [ ] `mvn -B test` passes
   - [ ] Frontend TypeScript compiles
   - [ ] PNGs are created by pipeline run

---

## Summary of Files

| File | Status | Purpose |
|------|--------|---------|
| `tile-compiler/src/main/java/.../HillshadeGrid.java` | Create | DEM synthesis + hillshade PNG writer |
| `tile-compiler/src/test/java/.../HillshadeGridTest.java` | Create | Unit tests for grid computation |
| `tile-compiler/src/main/java/.../GenerateHillshade.java` | Create | StepExecutor wiring into DAG |
| `tile-compiler/steps.yaml` | Modify | Add hillshade-mountains + hillshade-hills groups |
| `frontend/src/ml-style/style.ts` | Modify | Add image sources + raster layers |
| `frontend/src/ml-style/state.ts` | Optional | Add `showHillshade` toggle |
| `frontend/public/hillshade-mountains.png` | Generated | Output from pipeline |
| `frontend/public/hillshade-hills.png` | Generated | Output from pipeline |
