package io.github.pfwikis.util;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.proj4j.geodesic.GeoMath;
import org.locationtech.proj4j.geodesic.Geodesic;
import org.locationtech.proj4j.geodesic.GeodesicData;
import org.locationtech.proj4j.geodesic.GeodesicMask;
import org.locationtech.proj4j.geodesic.Pair;

import io.github.pfwikis.model.Geometry;
import io.github.pfwikis.model.LngLat;

/**
 * Computes ellipsoidal geodesic polygon area on the WGS84 ellipsoid. This
 * matches QGIS' {@code $area} when the project/processing ellipsoid is set to
 * the WGS84 ellipsoid (EPSG:7030).
 */
public final class GeometryArea {

    private static final Geodesic GEODESIC = Geodesic.WGS84;
    private static final double ELLIPSOID_AREA = GEODESIC.EllipsoidArea();
    private static final int INVERSE_MASK =
        GeodesicMask.LATITUDE
        | GeodesicMask.LONGITUDE
        | GeodesicMask.DISTANCE
        | GeodesicMask.AREA
        | GeodesicMask.LONG_UNROLL;

    private GeometryArea() {
    }

    public static double areaM2(Geometry geometry) {
        if (geometry instanceof Geometry.Polygon polygon) {
            return polygonAreaM2(polygon.getCoordinates());
        }
        if (geometry instanceof Geometry.MultiPolygon multiPolygon) {
            double result = 0;
            for (var polygon : multiPolygon.getCoordinates()) {
                result += polygonAreaM2(polygon);
            }
            return result;
        }
        // $area is zero for points and lines.
        return 0;
    }

    public static double areaSqKm(Geometry geometry) {
        return areaM2(geometry) / 1_000_000d;
    }

    private static double polygonAreaM2(List<List<LngLat>> rings) {
        if (rings == null || rings.isEmpty()) {
            return 0;
        }
        double result = ringAreaM2(rings.getFirst());
        for (int i = 1; i < rings.size(); i++) {
            result -= ringAreaM2(rings.get(i));
        }
        return Math.max(0, result);
    }

    private static double ringAreaM2(List<LngLat> ring) {
        var points = validPoints(ring);
        if (points.size() < 3) {
            return 0;
        }

        double previousLat = clampedLat(points.getFirst());
        double previousLng = points.getFirst().lng();
        double sum = 0;
        double compensation = 0;
        int crossings = 0;

        for (int i = 1; i <= points.size(); i++) {
            var point = i < points.size() ? points.get(i) : points.getFirst();
            double lat = clampedLat(point);
            double lng = point.lng();

            GeodesicData inverse = GEODESIC.Inverse(
                previousLat, previousLng, lat, lng, INVERSE_MASK
            );
            // Kahan summation keeps the area stable for high-vertex polygons.
            double adjusted = inverse.S12 - compensation;
            double next = sum + adjusted;
            compensation = (next - sum) - adjusted;
            sum = next;

            crossings += transit(previousLng, lng);
            previousLat = lat;
            previousLng = lng;
        }

        double area = Math.IEEEremainder(sum, ELLIPSOID_AREA);
        if ((crossings & 1) != 0) {
            area += (area < 0 ? 1 : -1) * ELLIPSOID_AREA / 2;
        }

        // PolygonArea.Compute(reverse=false, sign=true), followed by abs(),
        // makes the result independent of ring orientation.
        area = -area;
        if (area > ELLIPSOID_AREA / 2) {
            area -= ELLIPSOID_AREA;
        }
        else if (area <= -ELLIPSOID_AREA / 2) {
            area += ELLIPSOID_AREA;
        }

        return Double.isFinite(area) ? Math.abs(area) : 0;
    }

    private static List<LngLat> validPoints(List<LngLat> ring) {
        if (ring == null) {
            return List.of();
        }
        var result = new ArrayList<LngLat>(ring.size());
        for (var point : ring) {
            if (point == null
                || !Double.isFinite(point.lng())
                || !Double.isFinite(point.lat())) {
                return List.of();
            }
            result.add(point);
        }
        if (result.size() > 1 && samePoint(result.getFirst(), result.getLast())) {
            result.removeLast();
        }
        return result;
    }

    private static double clampedLat(LngLat point) {
        return Math.clamp(point.lat(), -90d, 90d);
    }

    private static boolean samePoint(LngLat a, LngLat b) {
        return Math.abs(a.lng() - b.lng()) < 1e-12
            && Math.abs(a.lat() - b.lat()) < 1e-12;
    }

    /*
     * Counts prime-meridian crossings exactly like GeographicLib's PolygonArea.
     * This is needed to resolve the area of polygons whose edges wrap around
     * the earth.
     */
    private static int transit(double lon1, double lon2) {
        var difference = new Pair();
        GeoMath.AngDiff(difference, lon1, lon2);
        lon1 = GeoMath.AngNormalize(lon1);
        lon2 = GeoMath.AngNormalize(lon2);
        double delta = difference.first;
        return delta > 0 && ((lon1 < 0 && lon2 >= 0) || (lon1 > 0 && lon2 == 0))
            ? 1
            : (delta < 0 && lon1 >= 0 && lon2 < 0 ? -1 : 0);
    }
}
