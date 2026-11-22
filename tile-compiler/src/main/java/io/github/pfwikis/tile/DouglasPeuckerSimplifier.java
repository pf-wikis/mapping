/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.locationtech.jts.geom.Coordinate
 *  org.locationtech.jts.geom.CoordinateSequence
 *  org.locationtech.jts.geom.Geometry
 *  org.locationtech.jts.geom.LinearRing
 *  org.locationtech.jts.geom.Polygon
 *  org.locationtech.jts.geom.util.GeometryTransformer
 */
package io.github.pfwikis.tile;



import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.util.GeometryTransformer;

public class DouglasPeuckerSimplifier
extends GeometryTransformer {
    private final double sqTolerance;

    public DouglasPeuckerSimplifier(double distanceTolerance) {
        this.sqTolerance = distanceTolerance * Math.abs(distanceTolerance);
    }

    public Geometry simplify(Geometry geom) {
        if (geom.isEmpty() || this.sqTolerance < 0.0) {
            return geom.copy();
        }
        return this.transform(geom);
    }

    public static Geometry simplify(Geometry geom, double distanceTolerance) {
        if (geom.isEmpty() || distanceTolerance < 0.0) {
            return geom.copy();
        }
        return new DouglasPeuckerSimplifier(distanceTolerance).simplify(geom);
    }

    public static List<Coordinate> simplify(List<Coordinate> coords, double distanceTolerance, boolean area) {
        if (coords.isEmpty()) {
            return List.of();
        }
        return new DouglasPeuckerSimplifier(distanceTolerance).transformCoordinateList(coords, area);
    }

    public static double getSqSegDist(double px, double py, double p1x, double p1y, double p2x, double p2y) {
        double x = p1x;
        double y = p1y;
        double dx = p2x - x;
        double dy = p2y - y;
        if (dx != 0.0 || dy != 0.0) {
            double t = ((px - x) * dx + (py - y) * dy) / (dx * dx + dy * dy);
            if (t > 1.0) {
                x = p2x;
                y = p2y;
            } else if (t > 0.0) {
                x += dx * t;
                y += dy * t;
            }
        }
        dx = px - x;
        dy = py - y;
        return dx * dx + dy * dy;
    }

    private void subsimplify(List<Coordinate> in, List<Coordinate> out, int first, int last, int numForcedPoints) {
        boolean force = numForcedPoints > 0;
        double maxSqDist = force ? -1.0 : this.sqTolerance;
        int index = -1;
        Coordinate p1 = in.get(first);
        Coordinate p2 = in.get(last);
        double p1x = p1.x;
        double p1y = p1.y;
        double p2x = p2.x;
        double p2y = p2.y;
        int i = first + 1;
        Coordinate furthest = null;
        for (Coordinate coord : in.subList(first + 1, last)) {
            double sqDist = DouglasPeuckerSimplifier.getSqSegDist(coord.x, coord.y, p1x, p1y, p2x, p2y);
            if (sqDist > maxSqDist) {
                index = i;
                furthest = coord;
                maxSqDist = sqDist;
            }
            ++i;
        }
        if (force || maxSqDist > this.sqTolerance) {
            if (index - first > 1) {
                this.subsimplify(in, out, first, index, numForcedPoints - 1);
            }
            out.add(furthest);
            if (last - index > 1) {
                this.subsimplify(in, out, index, last, numForcedPoints - 2);
            }
        }
    }

    private void subsimplify(CoordinateSequence in, MutableCoordinateSequence out, int first, int last, int numForcedPoints) {
        boolean force = numForcedPoints > 0;
        double maxSqDist = force ? -1.0 : this.sqTolerance;
        int index = -1;
        double p1x = in.getX(first);
        double p1y = in.getY(first);
        double p2x = in.getX(last);
        double p2y = in.getY(last);
        for (int i = first + 1; i < last; ++i) {
            double py;
            double px = in.getX(i);
            double sqDist = DouglasPeuckerSimplifier.getSqSegDist(px, py = in.getY(i), p1x, p1y, p2x, p2y);
            if (!(sqDist > maxSqDist)) continue;
            index = i;
            maxSqDist = sqDist;
        }
        if (force || maxSqDist > this.sqTolerance) {
            if (index - first > 1) {
                this.subsimplify(in, out, first, index, numForcedPoints - 1);
            }
            out.forceAddPoint(in.getX(index), in.getY(index));
            if (last - index > 1) {
                this.subsimplify(in, out, index, last, numForcedPoints - 2);
            }
        }
    }

    protected List<Coordinate> transformCoordinateList(List<Coordinate> coords, boolean area) {
        int minPoints;
        int n = minPoints = area ? 4 : 2;
        if (coords.size() <= minPoints) {
            return coords;
        }
        ArrayList<Coordinate> result = new ArrayList<Coordinate>();
        result.add(coords.getFirst());
        this.subsimplify(coords, result, 0, coords.size() - 1, minPoints - 2);
        result.add(coords.getLast());
        return result;
    }

    protected CoordinateSequence transformCoordinates(CoordinateSequence coords, Geometry parent) {
        int minPoints;
        boolean area = parent instanceof LinearRing;
        int n = minPoints = area ? 4 : 2;
        if (coords.size() <= minPoints) {
            return coords;
        }
        MutableCoordinateSequence result = new MutableCoordinateSequence();
        result.forceAddPoint(coords.getX(0), coords.getY(0));
        this.subsimplify(coords, result, 0, coords.size() - 1, minPoints - 2);
        result.forceAddPoint(coords.getX(coords.size() - 1), coords.getY(coords.size() - 1));
        return result;
    }

    protected Geometry transformPolygon(Polygon geom, Geometry parent) {
        return geom.isEmpty() ? null : super.transformPolygon(geom, parent);
    }

    protected Geometry transformLinearRing(LinearRing geom, Geometry parent) {
        boolean removeDegenerateRings = parent instanceof Polygon;
        Geometry simpResult = super.transformLinearRing(geom, parent);
        if (removeDegenerateRings && !(simpResult instanceof LinearRing)) {
            return null;
        }
        return simpResult;
    }
}

