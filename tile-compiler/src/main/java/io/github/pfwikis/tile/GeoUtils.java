package io.github.pfwikis.tile;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFactory;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.locationtech.jts.geom.util.GeometryTransformer;
import org.locationtech.jts.precision.GeometryPrecisionReducer;

final class GeoUtils {
	static final PrecisionModel TILE_PRECISION = new PrecisionModel(16.0);
	static final GeometryFactory JTS_FACTORY = new GeometryFactory((CoordinateSequenceFactory)PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
	static final Geometry EMPTY_GEOMETRY = JTS_FACTORY.createGeometryCollection();
	static final Point EMPTY_POINT = JTS_FACTORY.createPoint();
	static final LineString EMPTY_LINE = JTS_FACTORY.createLineString();
	static final Polygon EMPTY_POLYGON = JTS_FACTORY.createPolygon();
	private static final LineString[] EMPTY_LINE_STRING_ARRAY = new LineString[0];
	private static final Polygon[] EMPTY_POLYGON_ARRAY = new Polygon[0];
	private static final Point[] EMPTY_POINT_ARRAY = new Point[0];
	private static final double MAX_LAT = getWorldLat(-0.1);
	private static final double MIN_LAT = getWorldLat(1.1);

	private static final GeometryTransformer PROJECT_WORLD_COORDS = new GeometryTransformer() {
		@Override
		protected CoordinateSequence transformCoordinates(CoordinateSequence coords, Geometry parent) {
			var copy = new PackedCoordinateSequence.Double(coords.size(), 2, 0);
			for(int i=0; i<coords.size(); i++) {
				copy.setOrdinate(i, 0, getWorldX(coords.getX(i)));
				copy.setOrdinate(i, 1, getWorldY(coords.getY(i)));
			}
			return copy;
		}
	};

	private GeoUtils() {}

	static Geometry latLonToWorldCoords(Geometry geom) {
		return PROJECT_WORLD_COORDS.transform(geom);
	}

	static double getWorldLon(double x) {
		return x * 360d - 180d;
	}

	static double getWorldLat(double y) {
		double n = Math.PI - Math.PI * 2d * y;
		return Math.toDegrees(Math.atan(0.5 * (Math.exp(n) - Math.exp(-n))));
	}

	static double getWorldX(double longitude) {
		return (longitude + 180d) / 360d;
	}

	static double getWorldY(double latitude) {
		if(latitude <= MIN_LAT)
			return 1.1;
		if(latitude >= MAX_LAT)
			return -0.1;
		double sin = Math.sin(latitude * (Math.PI / 180d));
		return 0.5 - 0.25 * Math.log((1d + sin) / (1d - sin)) / Math.PI;
	}

	static CoordinateSequence coordinateSequence(double... coords) {
		return new PackedCoordinateSequence.Double(coords, 2, 0);
	}

	static MultiLineString createMultiLineString(List<LineString> lineStrings) {
		return JTS_FACTORY.createMultiLineString(lineStrings.toArray(EMPTY_LINE_STRING_ARRAY));
	}

	static MultiPolygon createMultiPolygon(List<Polygon> polygons) {
		return JTS_FACTORY.createMultiPolygon(polygons.toArray(EMPTY_POLYGON_ARRAY));
	}

	static MultiPoint createMultiPoint(List<Point> points) {
		return JTS_FACTORY.createMultiPoint(points.toArray(EMPTY_POINT_ARRAY));
	}

	static Geometry combineLineStrings(List<LineString> lineStrings) {
		return lineStrings.size() == 1 ? lineStrings.getFirst() : createMultiLineString(lineStrings);
	}

	static Geometry combinePolygons(List<Polygon> polygons) {
		return polygons.size() == 1 ? polygons.getFirst() : createMultiPolygon(polygons);
	}

	static Geometry combinePoints(List<Point> points) {
		return points.size() == 1 ? points.getFirst() : createMultiPoint(points);
	}

	static Geometry fixPolygon(Geometry geom) throws GeometryException {
		try {
			return geom.buffer(0d);
		} catch(TopologyException e) {
			throw new GeometryException("robustness error fixing polygon", e);
		}
	}

	static Geometry fixPolygon(Geometry geom, double buffer) throws GeometryException {
		try {
			return geom.buffer(buffer).buffer(-buffer);
		} catch(TopologyException e) {
			throw new GeometryException("robustness error fixing polygon", e);
		}
	}

	static Geometry snapAndFixPolygon(Geometry geom) throws GeometryException {
		try {
			Geometry naiveSnap = new PointwiseRounder(TILE_PRECISION).transform(geom);
			if(naiveSnap.isValid())
				return new OrientationFixer().transform(naiveSnap);
			if(!geom.isValid())
				geom = fixPolygon(geom);
			return GeometryPrecisionReducer.reduce(geom, TILE_PRECISION);
		} catch(IllegalArgumentException | TopologyException e) {
			geom = GeometryFixer.fix(geom);
			try {
				return GeometryPrecisionReducer.reduce(geom, TILE_PRECISION);
			} catch(IllegalArgumentException | TopologyException e2) {
				geom = fixPolygon(geom, TILE_PRECISION.gridSize() / 2d);
				try {
					return GeometryPrecisionReducer.reduce(geom, TILE_PRECISION);
				} catch(IllegalArgumentException | TopologyException e3) {
					throw new GeometryException("snap and fix failed", e3);
				}
			}
		}
	}

	static Geometry sortPolygonsByAreaDescending(Geometry geometry) {
		if(geometry instanceof MultiPolygon multiPolygon) {
			var areas = new ArrayList<PolyAndArea>(multiPolygon.getNumGeometries());
			for(int i=0; i<multiPolygon.getNumGeometries(); i++)
				areas.add(new PolyAndArea((Polygon)multiPolygon.getGeometryN(i)));
			areas.sort(null);
			return JTS_FACTORY.createMultiPolygon(areas.stream().map(PolyAndArea::poly).toArray(Polygon[]::new));
		}
		return geometry;
	}

	private record PolyAndArea(Polygon poly, double area) implements Comparable<PolyAndArea> {
		PolyAndArea(Polygon poly) {
			this(poly, org.locationtech.jts.algorithm.Area.ofRing(poly.getExteriorRing().getCoordinateSequence()));
		}

		@Override
		public int compareTo(PolyAndArea o) {
			return -Double.compare(area, o.area);
		}
	}

	static Geometry collectionOrSingle(List<? extends Geometry> geometries) {
		if(geometries.size() == 1)
			return geometries.getFirst();
		return JTS_FACTORY.createGeometryCollection(geometries.toArray(Geometry[]::new));
	}

	static List<Geometry> parts(Geometry geometry) {
		var result = new ArrayList<Geometry>();
		if(geometry instanceof GeometryCollection collection) {
			for(int i=0; i<collection.getNumGeometries(); i++)
				result.add(collection.getGeometryN(i));
		} else {
			result.add(geometry);
		}
		return result;
	}

	private static final class PointwiseRounder extends GeometryTransformer {
		private final PrecisionModel precisionModel;

		PointwiseRounder(PrecisionModel precisionModel) {
			this.precisionModel = precisionModel;
		}

		@Override
		protected CoordinateSequence transformCoordinates(CoordinateSequence coordinates, Geometry parent) {
			if(coordinates.size() < 4)
				return null;
			var result = new MutableCoordinateSequence(coordinates.size());
			double lastX = Double.MIN_VALUE;
			double lastY = Double.MIN_VALUE;
			for(int i=0; i<coordinates.size(); i++) {
				double x = precisionModel.makePrecise(coordinates.getX(i));
				double y = precisionModel.makePrecise(coordinates.getY(i));
				if(x != lastX || y != lastY)
					result.forceAddPoint(x, y);
				lastX = x;
				lastY = y;
			}
			return result.size() < 4 ? null : result;
		}
	}

	private static final class OrientationFixer extends GeometryTransformer {
		private Geometry lastPolygon;

		@Override
		protected Geometry transformLinearRing(LinearRing geom, Geometry parent) {
			boolean isOuter = lastPolygon != parent;
			lastPolygon = parent;
			return Orientation.isCCW(geom.getCoordinateSequence()) == isOuter ? geom.reverse() : geom;
		}
	}
}
