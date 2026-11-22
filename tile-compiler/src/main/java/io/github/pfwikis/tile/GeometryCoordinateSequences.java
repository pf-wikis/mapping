/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.locationtech.jts.algorithm.Area
 *  org.locationtech.jts.geom.CoordinateSequence
 *  org.locationtech.jts.geom.CoordinateSequences
 *  org.locationtech.jts.geom.Geometry
 *  org.locationtech.jts.geom.GeometryCollection
 *  org.locationtech.jts.geom.LineString
 *  org.locationtech.jts.geom.LinearRing
 *  org.locationtech.jts.geom.Point
 *  org.locationtech.jts.geom.Polygon
 */
package io.github.pfwikis.tile;



import java.util.ArrayList;
import java.util.List;
import org.locationtech.jts.algorithm.Area;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequences;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

class GeometryCoordinateSequences {
    GeometryCoordinateSequences() {
    }

    static List<List<CoordinateSequence>> extractGroups(Geometry geom, double minSize) {
        ArrayList<List<CoordinateSequence>> result = new ArrayList<List<CoordinateSequence>>();
        GeometryCoordinateSequences.extractGroups(geom, result, minSize);
        return result;
    }

    private static void extractGroups(Geometry geom, List<List<CoordinateSequence>> groups, double minSize) {
        if (!geom.isEmpty()) {
            if (geom instanceof GeometryCollection) {
                for (int i = 0; i < geom.getNumGeometries(); ++i) {
                    GeometryCoordinateSequences.extractGroups(geom.getGeometryN(i), groups, minSize);
                }
            } else if (geom instanceof Polygon) {
                Polygon polygon = (Polygon)geom;
                GeometryCoordinateSequences.extractGroupsFromPolygon(groups, minSize, polygon);
            } else if (geom instanceof LinearRing) {
                LinearRing linearRing = (LinearRing)geom;
                GeometryCoordinateSequences.extractGroups((Geometry)GeoUtils.JTS_FACTORY.createPolygon(linearRing), groups, minSize);
            } else if (geom instanceof LineString) {
                LineString lineString = (LineString)geom;
                if (lineString.getLength() >= minSize) {
                    groups.add(List.of(lineString.getCoordinateSequence()));
                }
            } else {
                throw new IllegalArgumentException("unrecognized geometry type: " + geom.getGeometryType());
            }
        }
    }

    private static void extractGroupsFromPolygon(List<List<CoordinateSequence>> groups, double minArea, Polygon polygon) {
        CoordinateSequence outer = polygon.getExteriorRing().getCoordinateSequence();
        double outerArea = Area.ofRingSigned((CoordinateSequence)outer);
        if (outerArea > 0.0) {
            CoordinateSequences.reverse((CoordinateSequence)outer);
        }
        if (Math.abs(outerArea) >= minArea) {
            ArrayList<CoordinateSequence> group = new ArrayList<CoordinateSequence>(1 + polygon.getNumInteriorRing());
            groups.add(group);
            group.add(outer);
            for (int i = 0; i < polygon.getNumInteriorRing(); ++i) {
                CoordinateSequence inner = polygon.getInteriorRingN(i).getCoordinateSequence();
                double innerArea = Area.ofRingSigned((CoordinateSequence)inner);
                if (innerArea > 0.0) {
                    CoordinateSequences.reverse((CoordinateSequence)inner);
                }
                if (!(Math.abs(innerArea) >= minArea)) continue;
                group.add(inner);
            }
        }
    }

    static Geometry reassembleLineStrings(List<List<CoordinateSequence>> geoms) {
        ArrayList<LineString> lineStrings = new ArrayList<LineString>();
        for (List<CoordinateSequence> inner : geoms) {
            for (CoordinateSequence coordinateSequence : inner) {
                if (coordinateSequence.size() <= 1) continue;
                lineStrings.add(GeoUtils.JTS_FACTORY.createLineString(coordinateSequence));
            }
        }
        return GeoUtils.combineLineStrings(lineStrings);
    }

    static Geometry reassemblePolygons(List<List<CoordinateSequence>> groups) throws GeometryException {
        int numGeoms = groups.size();
        if (numGeoms == 1) {
            return GeometryCoordinateSequences.reassemblePolygon(groups.getFirst());
        }
        Polygon[] polygons = new Polygon[numGeoms];
        for (int i = 0; i < numGeoms; ++i) {
            polygons[i] = GeometryCoordinateSequences.reassemblePolygon(groups.get(i));
        }
        return GeoUtils.JTS_FACTORY.createMultiPolygon(polygons);
    }

    private static Polygon reassemblePolygon(List<CoordinateSequence> group) throws GeometryException {
        try {
            LinearRing first = GeoUtils.JTS_FACTORY.createLinearRing(group.getFirst());
            LinearRing[] rest = new LinearRing[group.size() - 1];
            for (int j = 1; j < group.size(); ++j) {
                CoordinateSequence seq = group.get(j);
                CoordinateSequences.reverse((CoordinateSequence)seq);
                rest[j - 1] = GeoUtils.JTS_FACTORY.createLinearRing(seq);
            }
            return GeoUtils.JTS_FACTORY.createPolygon(first, rest);
        }
        catch (IllegalArgumentException e) {
            throw new GeometryException("reassemble_polygon_failed", "Could not build polygon", e);
        }
    }

    static Geometry reassemblePoints(List<List<CoordinateSequence>> result) {
        ArrayList<Point> points = new ArrayList<Point>();
        for (List<CoordinateSequence> inner : result) {
            for (CoordinateSequence coordinateSequence : inner) {
                if (coordinateSequence.size() != 1) continue;
                points.add(GeoUtils.JTS_FACTORY.createPoint(coordinateSequence));
            }
        }
        return GeoUtils.combinePoints(points);
    }
}

