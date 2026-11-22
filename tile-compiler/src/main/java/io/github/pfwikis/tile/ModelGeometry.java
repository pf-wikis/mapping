package io.github.pfwikis.tile;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import io.github.pfwikis.model.LngLat;

final class ModelGeometry {
	private ModelGeometry() {}

	static Geometry toJts(io.github.pfwikis.model.Geometry geometry) {
		if(geometry instanceof io.github.pfwikis.model.Geometry.Point point) {
			return GeoUtils.JTS_FACTORY.createPoint(coordinate(point.getCoordinates()));
		}
		if(geometry instanceof io.github.pfwikis.model.Geometry.MultiPoint multiPoint) {
			var points = new ArrayList<Point>(multiPoint.getCoordinates().size());
			for(var point : multiPoint.getCoordinates())
				points.add(GeoUtils.JTS_FACTORY.createPoint(coordinate(point)));
			return GeoUtils.JTS_FACTORY.createMultiPoint(points.toArray(Point[]::new));
		}
		if(geometry instanceof io.github.pfwikis.model.Geometry.LineString lineString) {
			return GeoUtils.JTS_FACTORY.createLineString(coordinates(lineString.getCoordinates()));
		}
		if(geometry instanceof io.github.pfwikis.model.Geometry.MultiLineString multiLineString) {
			var lines = new ArrayList<LineString>(multiLineString.getCoordinates().size());
			for(var line : multiLineString.getCoordinates())
				lines.add(GeoUtils.JTS_FACTORY.createLineString(coordinates(line)));
			return GeoUtils.JTS_FACTORY.createMultiLineString(lines.toArray(LineString[]::new));
		}
		if(geometry instanceof io.github.pfwikis.model.Geometry.Polygon polygon) {
			return polygon(polygon.getCoordinates());
		}
		if(geometry instanceof io.github.pfwikis.model.Geometry.MultiPolygon multiPolygon) {
			var polygons = new ArrayList<Polygon>(multiPolygon.getCoordinates().size());
			for(var polygon : multiPolygon.getCoordinates())
				polygons.add(polygon(polygon));
			return GeoUtils.JTS_FACTORY.createMultiPolygon(polygons.toArray(Polygon[]::new));
		}
		throw new IllegalArgumentException("Unsupported geometry "+geometry);
	}

	private static Polygon polygon(List<List<LngLat>> rings) {
		LinearRing shell = GeoUtils.JTS_FACTORY.createLinearRing(coordinates(rings.getFirst()));
		var holes = new LinearRing[rings.size()-1];
		for(int i=1; i<rings.size(); i++)
			holes[i-1] = GeoUtils.JTS_FACTORY.createLinearRing(coordinates(rings.get(i)));
		return GeoUtils.JTS_FACTORY.createPolygon(shell, holes);
	}

	private static Coordinate coordinate(LngLat point) {
		return new Coordinate(point.lng(), point.lat());
	}

	private static Coordinate[] coordinates(List<LngLat> points) {
		var result = new Coordinate[points.size()];
		for(int i=0; i<points.size(); i++)
			result[i] = coordinate(points.get(i));
		return result;
	}
}
