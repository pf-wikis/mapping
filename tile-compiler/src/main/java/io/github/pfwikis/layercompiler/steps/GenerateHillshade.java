package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.model.Feature;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class GenerateHillshade extends StepExecutor {

    // Fixed DEM resolution for the full-resolution grid before tile subsampling.
    // These are separate from the tile output; data is computed once at high res then subsampled per tile.
    private static final int RES_X = 4096;
    private static final int RES_Y = 2048;

    private String layer;
    private int maxElevation = 255;
    private double power = 1.0;
    private boolean includeLocations = false;
    private boolean emitBounds = true;
    private int maxZoom = 5;
    private int tileSize = 256;

    // Optional explicit world bounds. If any is NaN, auto-computed from data.
    private double boundsMinX = Double.NaN;
    private double boundsMaxX = Double.NaN;
    private double boundsMinY = Double.NaN;
    private double boundsMaxY = Double.NaN;

    @Override
    public Content process(Inputs in) throws Exception {
        log.info("Generating hillshade for '{}' with maxElevation={}, power={}", layer, maxElevation, power);

        var polygonFc = in.getInput("polygons").toFeatureCollection();
        List<Geometry> polygons = new ArrayList<>();
        for (var f : polygonFc.getFeatures()) {
            var geom = convertToJts(f);
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
            log.info("  Merged {} mountain points from locations as bumps", pointBumps.size());
        }

        boolean useExplicit = !Double.isNaN(boundsMinX) && !Double.isNaN(boundsMaxX)
                           && !Double.isNaN(boundsMinY) && !Double.isNaN(boundsMaxY);

        HillshadeGrid grid;
        if (useExplicit) {
            grid = new HillshadeGrid(RES_X, RES_Y, boundsMinX, boundsMaxX, boundsMinY, boundsMaxY,
                    polygons, maxElevation, power, pointBumps, 200.0, 0.3);
        } else {
            grid = new HillshadeGrid(RES_X, RES_Y, polygons, maxElevation, power, pointBumps, 200.0, 0.3);
        }

        var targetDir = Ctx.INSTANCE.getOptions().targetDirectory();
        var genDir = Ctx.INSTANCE.getOptions().targetGenDirectory();

        // Write terrarium-encoded DEM tiles
        grid.writeDemTiles(targetDir, layer, maxZoom, tileSize);
        log.info("  Wrote terrarium DEM tiles for '{}' up to zoom {}", layer, maxZoom);

        if (emitBounds) {
            writeBoundsTs(genDir, layer, grid);
        }

        return Content.empty();
    }

    private void writeBoundsTs(File genDir, String layerName, HillshadeGrid grid) throws Exception {
        var tsFile = new File(genDir, "hillshade-bounds-" + layerName + ".ts");
        // Maplibre image source coordinates: [tl, tr, br, bl] = [[minLng, maxLat], [maxLng, maxLat], [maxLng, minLat], [minLng, minLat]]
        String content = String.format(Locale.US,
            "export const hillshade%sBounds = [%n"
            + "  [%s, %s],%n"  // tl
            + "  [%s, %s],%n"  // tr
            + "  [%s, %s],%n"  // br
            + "  [%s, %s],%n"  // bl
            + "] as [[number, number], [number, number], [number, number], [number, number]];%n",
            capitalize(layerName),
            grid.getMinX(), grid.getMaxY(),
            grid.getMaxX(), grid.getMaxY(),
            grid.getMaxX(), grid.getMinY(),
            grid.getMinX(), grid.getMinY()
        );
        Files.writeString(tsFile.toPath(), content);
        log.info("  Wrote bounds file {} for layer '{}'", tsFile.getAbsolutePath(), layerName);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private Geometry convertToJts(Feature f) {
        if (f == null || f.getGeometry() == null) return null;
        var modelGeom = f.getGeometry();
        if (modelGeom instanceof io.github.pfwikis.model.Geometry.Polygon p) {
            var shell = ringToJts(p.getCoordinates().get(0));
            var holes = new org.locationtech.jts.geom.LinearRing[p.getCoordinates().size() - 1];
            for (int i = 1; i < p.getCoordinates().size(); i++) {
                holes[i - 1] = ringToJts(p.getCoordinates().get(i));
            }
            return new org.locationtech.jts.geom.GeometryFactory().createPolygon(shell, holes);
        } else if (modelGeom instanceof io.github.pfwikis.model.Geometry.MultiPolygon mp) {
            var polys = new org.locationtech.jts.geom.Polygon[mp.getCoordinates().size()];
            int idx = 0;
            for (var polyCoors : mp.getCoordinates()) {
                var shell = ringToJts(polyCoors.get(0));
                var holes = new org.locationtech.jts.geom.LinearRing[polyCoors.size() - 1];
                for (int i = 1; i < polyCoors.size(); i++) {
                    holes[i - 1] = ringToJts(polyCoors.get(i));
                }
                polys[idx++] = new org.locationtech.jts.geom.GeometryFactory().createPolygon(shell, holes);
            }
            return new org.locationtech.jts.geom.GeometryFactory().createMultiPolygon(polys);
        }
        return null;
    }

    private org.locationtech.jts.geom.LinearRing ringToJts(List<io.github.pfwikis.model.LngLat> ring) {
        var coords = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            var ll = ring.get(i);
            coords[i] = new Coordinate(ll.lng(), ll.lat());
        }
        return new org.locationtech.jts.geom.GeometryFactory().createLinearRing(coords);
    }
}
