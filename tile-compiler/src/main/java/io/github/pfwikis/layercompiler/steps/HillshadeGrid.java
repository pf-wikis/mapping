package io.github.pfwikis.layercompiler.steps;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import lombok.extern.slf4j.Slf4j;

/**
 * Synthesizes a hillshade image from 2D polygon features using distance-to-boundary transforms.
 *
 * Algorithm:
 * 1. Determine world bounds from all input geometries (with 5% padding), or use explicit bounds.
 * 2. Rasterize each polygon and compute distance to boundary per cell.
 * 3. Per-polygon: normalize distance and apply elevation formula.
 * 4. Merge all polygons by taking the per-cell maximum.
 * 5. Optionally add point bumps (gaussian) from mountain locations.
 * 6. Compute hillshade from DEM with fixed lighting (az=315, alt=45, z=1).
 * 7. Write RGBA PNG + world file (.wld) for georeferencing.
 *    Cells outside all polygons/pointed-bumps have alpha=0 (transparent).
 */
@Slf4j
public class HillshadeGrid {

    // world coordinate bounds
    private final double minX, maxX, minY, maxY;
    private final int width, height;
    private final double cellSizeX, cellSizeY;
    private final float[][] dem;
    private final boolean[][] covered;  // true = cell was touched by at least one polygon or bump

    private static final GeometryFactory GF = new GeometryFactory();

    /**
     * Auto-compute bounds from input geometries.
     */
    public HillshadeGrid(int width, int height, List<Geometry> polygons,
                         int maxElevation, double power,
                         List<Coordinate> pointBumps, double bumpPeak, double bumpSigmaDegrees) {
        this(width, height, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
             polygons, maxElevation, power, pointBumps, bumpPeak, bumpSigmaDegrees);
    }

    /**
     * Use explicit bounds. If any bound is NaN, falls back to auto-compute.
     */
    public HillshadeGrid(int width, int height,
                         double explicitMinX, double explicitMaxX, double explicitMinY, double explicitMaxY,
                         List<Geometry> polygons, int maxElevation, double power,
                         List<Coordinate> pointBumps, double bumpPeak, double bumpSigmaDegrees) {
        boolean useExplicit = !Double.isNaN(explicitMinX) && !Double.isNaN(explicitMaxX)
                           && !Double.isNaN(explicitMinY) && !Double.isNaN(explicitMaxY);
        if (useExplicit) {
            this.minX = explicitMinX;
            this.maxX = explicitMaxX;
            this.minY = explicitMinY;
            this.maxY = explicitMaxY;
        } else {
            Envelope env = new Envelope();
            for (var g : polygons) {
                if (g != null && !g.isEmpty()) {
                    env.expandToInclude(g.getEnvelopeInternal());
                }
            }
            for (var c : pointBumps) {
                env.expandToInclude(c);
            }
            double padX = Math.max((env.getMaxX() - env.getMinX()) * 0.05, 1.0);
            double padY = Math.max((env.getMaxY() - env.getMinY()) * 0.05, 1.0);
            this.minX = env.getMinX() - padX;
            this.maxX = env.getMaxX() + padX;
            this.minY = env.getMinY() - padY;
            this.maxY = env.getMaxY() + padY;
        }

        this.width = width;
        this.height = height;
        this.cellSizeX = (maxX - minX) / width;
        this.cellSizeY = (maxY - minY) / height;
        this.dem = new float[height][width];
        this.covered = new boolean[height][width];

        for (var poly : polygons) {
            if (poly != null && !poly.isEmpty()) {
                addPolygon(poly, maxElevation, power);
            }
        }

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
        int minRow = worldYToRow(env.getMaxY());
        int maxRow = worldYToRow(env.getMinY());
        minCol = clamp(minCol, 0, width - 1);
        maxCol = clamp(maxCol, 0, width - 1);
        minRow = clamp(minRow, 0, height - 1);
        maxRow = clamp(maxRow, 0, height - 1);

        boolean[][] inside = new boolean[maxRow - minRow + 1][maxCol - minCol + 1];
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                Coordinate c = new Coordinate(colToWorldX(col), rowToWorldY(row));
                Point p = GF.createPoint(c);
                inside[row - minRow][col - minCol] = poly.contains(p);
            }
        }

        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        boolean[][] boundary = new boolean[maxRow - minRow + 1][maxCol - minCol + 1];
        List<int[]> boundaryCells = new ArrayList<>();
        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (!inside[row - minRow][col - minCol]) continue;
                boolean isBoundary = false;
                for (int d = 0; d < 4; d++) {
                    int nr = row + dr[d];
                    int nc = col + dc[d];
                    if (nr < minRow || nr > maxRow || nc < minCol || nc > maxCol) {
                        Coordinate nc2 = new Coordinate(colToWorldX(nc), rowToWorldY(nr));
                        if (!poly.contains(GF.createPoint(nc2))) {
                            isBoundary = true;
                            break;
                        }
                    } else if (!inside[nr - minRow][nc - minCol]) {
                        isBoundary = true;
                        break;
                    }
                }
                if (isBoundary) {
                    boundary[row - minRow][col - minCol] = true;
                    boundaryCells.add(new int[]{row - minRow, col - minCol});
                }
            }
        }

        if (boundaryCells.isEmpty()) return;

        float[][] dist = new float[maxRow - minRow + 1][maxCol - minCol + 1];
        for (float[] row : dist) Arrays.fill(row, Float.MAX_VALUE);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int[] b : boundaryCells) {
            dist[b[0]][b[1]] = 0f;
            queue.add(b);
        }
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            float cd = dist[cur[0]][cur[1]];
            for (int d = 0; d < 4; d++) {
                int nr = cur[0] + dr[d];
                int nc = cur[1] + dc[d];
                if (nr < 0 || nr > maxRow - minRow || nc < 0 || nc > maxCol - minCol) continue;
                if (!inside[nr][nc]) continue;
                float nd = cd + 1f;
                if (nd < dist[nr][nc]) {
                    dist[nr][nc] = nd;
                    queue.add(new int[]{nr, nc});
                }
            }
        }

        float maxDist = 0f;
        for (int row = 0; row <= maxRow - minRow; row++) {
            for (int col = 0; col <= maxCol - minCol; col++) {
                if (inside[row][col] && dist[row][col] > maxDist && dist[row][col] != Float.MAX_VALUE) {
                    maxDist = dist[row][col];
                }
            }
        }
        if (maxDist <= 1f) return;

        for (int row = minRow; row <= maxRow; row++) {
            for (int col = minCol; col <= maxCol; col++) {
                if (inside[row - minRow][col - minCol]) {
                    float d = dist[row - minRow][col - minCol];
                    if (d == Float.MAX_VALUE) continue;
                    double norm = Math.max(0, Math.min(1, d / maxDist));
                    float elev = (float) (maxElev * Math.pow(norm, power));
                    if (elev > dem[row][col]) {
                        dem[row][col] = elev;
                    }
                    if (elev > 0.0f || d == 0f) {
                        covered[row][col] = true;
                    }
                }
            }
        }
    }

    private void addPointBump(Coordinate c, double peak, double sigmaDegrees) {
        int centerCol = worldXToCol(c.x);
        int centerRow = worldYToRow(c.y);
        double sigmaPixels = sigmaDegrees / cellSizeY;
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
                if (bump > 1.0) {
                    covered[row][col] = true;
                }
            }
        }
    }

    /**
     * Terrarium encode a single elevation value (0..255 range supported).
     * Returns [red, green, blue].
     */
    public static int[] encodeTerrarium(int elevation) {
        int value = elevation + 32768;
        int red = value / 256;
        int green = value % 256;
        int blue = 0;
        return new int[]{red, green, blue};
    }

    /**
     * Terrarium decode RGB values back to elevation.
     */
    public static int decodeTerrarium(int red, int green, int blue) {
        double decoded = (red * 256.0 + green + blue / 256.0) - 32768.0;
        return (int) Math.round(decoded);
    }

    /**
     * Write terrarium-encoded DEM tiles for multiple zoom levels.
     * Output directory: outputDir/hillshade-{layerName}/{z}/{x}/{y}.png
     */
    public void writeDemTiles(File outputDir, String layerName, int maxZoom, int tileSize) throws IOException {
        File baseDir = new File(outputDir, "hillshade-" + layerName);
        baseDir.mkdirs();

        for (int z = 0; z <= maxZoom; z++) {
            // find tiles that overlap the data bounds
            int minTx = lonToTileX(minX, z);
            int maxTx = lonToTileX(maxX, z);
            int minTy = latToTileY(maxY, z); // maxY is north (smaller ty)
            int maxTy = latToTileY(minY, z); // minY is south (larger ty)

            for (int tx = minTx; tx <= maxTx; tx++) {
                for (int ty = minTy; ty <= maxTy; ty++) {
                    BufferedImage tile = createDemTile(z, tx, ty, tileSize);
                    if (tile != null) {
                        File tileDir = new File(baseDir, z + "/" + tx);
                        tileDir.mkdirs();
                        File tileFile = new File(tileDir, ty + ".png");
                        ImageIO.write(tile, "png", tileFile);
                    }
                }
            }
        }
    }

    private BufferedImage createDemTile(int z, int tx, int ty, int tileSize) {
        // tile bounds in lat/lon
        double west = tileXToLon(tx, z);
        double east = tileXToLon(tx + 1, z);
        double north = tileYToLat(ty, z);
        double south = tileYToLat(ty + 1, z);

        BufferedImage img = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_RGB);
        boolean hasData = false;
        float maxElevInTile = 0.0f;

        for (int row = 0; row < tileSize; row++) {
            double lat = north - (north - south) * (row + 0.5) / tileSize;
            for (int col = 0; col < tileSize; col++) {
                double lon = west + (east - west) * (col + 0.5) / tileSize;
                float e = sampleDem(lon, lat);
                int elev = (int) Math.round(e);
                if (elev < 0) elev = 0;
                if (e > maxElevInTile) maxElevInTile = e;
                if (elev > 0) hasData = true;

                int[] rgb = encodeTerrarium(elev);
                int color = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
                img.setRGB(col, row, color);
            }
        }

        return (maxElevInTile >= 1.0f) ? img : null;
    }

    /**
     * Sample the DEM at a given lat/lon using bilinear interpolation.
     * Returns 0 if outside the grid.
     */
    private float sampleDem(double lon, double lat) {
        double dcol = (lon - minX) / cellSizeX;
        double drow = (maxY - lat) / cellSizeY;

        int col0 = (int) Math.floor(dcol);
        int row0 = (int) Math.floor(drow);
        double fx = dcol - col0;
        double fy = drow - row0;

        // Bilinear interpolation from 4 neighbors
        float v00 = samplePixel(col0, row0);
        float v10 = samplePixel(col0 + 1, row0);
        float v01 = samplePixel(col0, row0 + 1);
        float v11 = samplePixel(col0 + 1, row0 + 1);

        float v0 = v00 * (1 - (float) fx) + v10 * (float) fx;
        float v1 = v01 * (1 - (float) fx) + v11 * (float) fx;
        return v0 * (1 - (float) fy) + v1 * (float) fy;
    }

    private float samplePixel(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) return 0f;
        return dem[row][col];
    }

    /* --- Web Mercator tile math (lat/lon variant) --- */

    private static int lonToTileX(double lon, int zoom) {
        int n = 1 << zoom;
        return clamp((int) Math.floor(((lon + 180.0) / 360.0) * n), 0, n - 1);
    }

    private static int latToTileY(double lat, int zoom) {
        int n = 1 << zoom;
        double latRad = Math.toRadians(lat);
        return clamp((int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n), 0, n - 1);
    }

    private static double tileXToLon(int x, int zoom) {
        return x / (double) (1 << zoom) * 360.0 - 180.0;
    }

    private static double tileYToLat(int y, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * y / (double) (1 << zoom);
        return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
    }

    public void writeHillshade(File pngFile) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
        byte[] pixels = ((java.awt.image.DataBufferByte) img.getRaster().getDataBuffer()).getData();

        double zenith = Math.toRadians(90.0 - 45.0);
        double azimuthRad = Math.toRadians(360.0 - 315.0 + 90.0);
        double zFactor = 1.0;

        for (int row = 1; row < height - 1; row++) {
            for (int col = 1; col < width - 1; col++) {
                byte alpha = covered[row][col] ? (byte) 255 : 0;
                byte value;
                if (covered[row][col]) {
                    double dx = (dem[row][col + 1] - dem[row][col - 1]) / (2 * cellSizeX);
                    double dy = (dem[row + 1][col] - dem[row - 1][col]) / (2 * cellSizeY);
                    double slope = Math.atan(zFactor * Math.sqrt(dx * dx + dy * dy));
                    double aspect;
                    if (dx == 0) {
                        aspect = dy > 0 ? Math.PI / 2 : dy == 0 ? 0 : -Math.PI / 2;
                    } else {
                        aspect = Math.atan2(dy, -dx);
                    }
                    double hillshade = 255.0 * ((Math.cos(zenith) * Math.cos(slope))
                            + (Math.sin(zenith) * Math.sin(slope) * Math.cos(azimuthRad - aspect)));
                    hillshade = Math.max(0, Math.min(255, hillshade));
                    value = (byte) (int) hillshade;
                } else {
                    value = 0;
                }
                // ABGR order: A = pixels[i*4+0], B = pixels[i*4+1], G = i*4+2, R = i*4+3
                int idx = (row * width + col) * 4;
                pixels[idx + 0] = alpha;  // A
                pixels[idx + 1] = value;  // B
                pixels[idx + 2] = value;  // G
                pixels[idx + 3] = value;  // R
            }
        }

        // Edge pixels: transparent black
        for (int row = 0; row < height; row++) {
            for (int col : new int[]{0, width - 1}) {
                int idx = (row * width + col) * 4;
                pixels[idx + 0] = 0;
                pixels[idx + 1] = 0;
                pixels[idx + 2] = 0;
                pixels[idx + 3] = 0;
            }
        }
        for (int col = 0; col < width; col++) {
            for (int row : new int[]{0, height - 1}) {
                int idx = (row * width + col) * 4;
                pixels[idx + 0] = 0;
                pixels[idx + 1] = 0;
                pixels[idx + 2] = 0;
                pixels[idx + 3] = 0;
            }
        }

        ImageIO.write(img, "png", pngFile);
        writeWorldFile(new File(pngFile.getParent(), pngFile.getName().replace(".png", ".wld")));
    }

    private void writeWorldFile(File wldFile) throws IOException {
        String content = String.format(Locale.US,
            "%s\n0.0\n0.0\n%s\n%s\n%s\n",
            cellSizeX,
            -cellSizeY,
            minX + cellSizeX / 2.0,
            maxY + cellSizeY / 2.0
        );
        java.nio.file.Files.writeString(wldFile.toPath(), content);
    }

    private int worldXToCol(double x) {
        return (int) Math.floor((x - minX) / cellSizeX);
    }
    private int worldYToRow(double y) {
        return (int) Math.floor((maxY - y) / cellSizeY);
    }
    private double colToWorldX(int col) {
        return minX + col * cellSizeX + cellSizeX / 2.0;
    }
    private double rowToWorldY(int row) {
        return maxY - row * cellSizeY - cellSizeY / 2.0;
    }
    public double getMinX() { return minX; }
    public double getMaxX() { return maxX; }
    public double getMinY() { return minY; }
    public double getMaxY() { return maxY; }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
