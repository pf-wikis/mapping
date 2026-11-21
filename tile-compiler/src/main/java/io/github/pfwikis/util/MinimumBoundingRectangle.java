package io.github.pfwikis.util;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;

import io.github.pfwikis.model.Geometry;
import io.github.pfwikis.model.LngLat;

/**
 * Java port of QGIS' oriented minimum bounding rectangle calculation.
 *
 * <p>QGIS uses a rotating-calipers-like algorithm over the convex hull and
 * exposes the result as width, height and an angle in degrees clockwise from
 * north. The field names and angle convention are preserved here so label
 * orientation stays identical to the previous QGIS-based implementation.</p>
 */
public final class MinimumBoundingRectangle {

    private static final GeometryFactory JTS_FACTORY = new GeometryFactory();

    private MinimumBoundingRectangle() {
    }

    public record Result(double width, double height, double angle) {
    }

    public static Result calculate(Geometry geometry) {
        var jtsGeometry = toJts(geometry);
        if (jtsGeometry == null) {
            return new Result(0, 0, 0);
        }

        var hull = jtsGeometry.convexHull();
        var coordinates = hull.getCoordinates();
        if (coordinates.length < 2) {
            return new Result(0, 0, 0);
        }

        int count = coordinates.length;
        if (samePoint(coordinates[0], coordinates[count - 1])) {
            count--;
        }
        if (count < 3) {
            return new Result(0, 0, 0);
        }

        var points = new ArrayList<Coordinate>(count);
        for (int i = 0; i < count; i++) {
            points.add(new Coordinate(coordinates[i]));
        }

        double originX = points.getFirst().x;
        double originY = points.getFirst().y;
        double area = Double.MAX_VALUE;
        double angle = 0;
        double width = Double.MAX_VALUE;
        double height = Double.MAX_VALUE;
        double totalRotation = 0;
        int previousIndex = 0;

        for (int i = 1; i <= count; i++) {
            int currentIndex = i % count;
            var previous = points.get(previousIndex);
            var current = points.get(currentIndex);

            double currentAngle = lineAngle(previous.x, previous.y, current.x, current.y);
            double rotateAngle = Math.toDegrees(currentAngle);
            totalRotation += rotateAngle;
            rotate(points, originX, originY, rotateAngle, count == 3);

            double minX = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (var point : points) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }

            double currentWidth = maxX - minX;
            double currentHeight = maxY - minY;
            double currentArea = currentWidth * currentHeight;
            if (currentArea < area) {
                area = currentArea;
                angle = totalRotation;
                width = currentWidth;
                height = currentHeight;
            }

            previousIndex = currentIndex;
        }

        if (width > height) {
            double temporary = width;
            width = height;
            height = temporary;
            angle += 90;
        }

        if (angle > 180) {
            angle = angle % 180;
        }

        return new Result(width, height, angle);
    }

    private static void rotate(
            List<Coordinate> points,
            double originX,
            double originY,
            double rotateAngle,
            boolean useDirectRotation) {
        double radians = Math.toRadians(rotateAngle);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        if (useDirectRotation) {
            /*
             * A triangle has three equal-area candidate rectangles; QGIS'
             * floating-point tie-breaking selects the same candidate as this
             * direct rotation form. For larger hulls the QTransform-composed
             * form below matches QGIS.
             */
            for (var point : points) {
                double dx = point.x - originX;
                double dy = point.y - originY;
                point.x = originX + dx * cos - dy * sin;
                point.y = originY + dx * sin + dy * cos;
            }
        }
        else {
            // QTransform: translate(origin) * rotate * translate(-origin)
            double translateX = originX - originX * cos + originY * sin;
            double translateY = originY - originY * cos - originX * sin;
            for (var point : points) {
                double x = point.x;
                double y = point.y;
                point.x = cos * x - sin * y + translateX;
                point.y = sin * x + cos * y + translateY;
            }
        }
    }

    private static double lineAngle(double x1, double y1, double x2, double y2) {
        return normalizedAngle(-Math.atan2(y2 - y1, x2 - x1) + Math.PI / 2);
    }

    private static double normalizedAngle(double angle) {
        double clipped = angle;
        if (clipped >= Math.PI * 2 || clipped <= -2 * Math.PI) {
            clipped = clipped % (2 * Math.PI);
        }
        if (clipped < 0) {
            clipped += 2 * Math.PI;
        }
        return clipped;
    }

    private static org.locationtech.jts.geom.Geometry toJts(Geometry geometry) {
        if (geometry instanceof Geometry.Polygon polygon) {
            return toJtsPolygon(polygon.getCoordinates());
        }
        if (geometry instanceof Geometry.MultiPolygon multiPolygon) {
            var polygons = new ArrayList<org.locationtech.jts.geom.Polygon>();
            for (var polygon : multiPolygon.getCoordinates()) {
                var converted = toJtsPolygon(polygon);
                if (converted != null) {
                    polygons.add(converted);
                }
            }
            if (polygons.isEmpty()) {
                return null;
            }
            return JTS_FACTORY.createMultiPolygon(
                polygons.toArray(org.locationtech.jts.geom.Polygon[]::new)
            );
        }
        return null;
    }

    private static org.locationtech.jts.geom.Polygon toJtsPolygon(List<List<LngLat>> rings) {
        if (rings == null || rings.isEmpty() || rings.getFirst().size() < 3) {
            return null;
        }
        var shell = toJtsRing(rings.getFirst());
        var holes = rings.stream()
            .skip(1)
            .filter(ring -> ring.size() >= 3)
            .map(MinimumBoundingRectangle::toJtsRing)
            .toArray(org.locationtech.jts.geom.LinearRing[]::new);
        return JTS_FACTORY.createPolygon(shell, holes);
    }

    private static org.locationtech.jts.geom.LinearRing toJtsRing(List<LngLat> ring) {
        var coordinates = new Coordinate[ring.size()];
        for (int i = 0; i < ring.size(); i++) {
            var point = ring.get(i);
            coordinates[i] = new Coordinate(point.lng(), point.lat());
        }
        return JTS_FACTORY.createLinearRing(coordinates);
    }

    private static boolean samePoint(Coordinate a, Coordinate b) {
        return Math.abs(a.x - b.x) < 1e-12 && Math.abs(a.y - b.y) < 1e-12;
    }
}
