package io.github.pfwikis.tile;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.Puntal;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

/**
 * Geometry encoding equivalent to Planetiler's VectorTile command representation.
 * Encoding then decoding to tile extent gives exactly the coordinates Planetiler
 * passes into MltConverter.
 */
final class TileGeometryCodec {

	enum GeometryType {
		POINT(1),
		LINE(2),
		POLYGON(4);

		private final int minPoints;

		GeometryType(int minPoints) {
			this.minPoints = minPoints;
		}

		int minPoints() {
			return minPoints;
		}

		static GeometryType typeOf(Geometry geom) {
			if(geom instanceof Puntal)
				return POINT;
			if(geom instanceof org.locationtech.jts.geom.Lineal)
				return LINE;
			if(geom instanceof org.locationtech.jts.geom.Polygonal)
				return POLYGON;
			return POINT;
		}
	}

	private enum Command {
		MOVE_TO(1),
		LINE_TO(2),
		CLOSE_PATH(7);

		final int value;

		Command(int value) {
			this.value = value;
		}
	}

	private record Encoded(int[] commands, GeometryType type, int scale) {}

	record EncodedGeometry(GeometryType type, int[] commands) {}

	static EncodedGeometry encodeToCommands(Geometry geometry, int scale) {
		var encoded = encode(geometry, scale);
		int[] commands = encoded.commands;
		if(scale > 0)
			commands = unscale(commands, scale, encoded.type);
		return new EncodedGeometry(encoded.type, commands);
	}

	static Geometry decode(EncodedGeometry encoded) throws GeometryException {
		return decodeCommands(encoded.type(), encoded.commands(), 1.0);
	}

	static Geometry quantize(Geometry geometry, int scale) throws GeometryException {
		return decode(encodeToCommands(geometry, scale));
	}

	/** Command representation of Planetiler's filled-tile rectangle (already in extent coordinates). */
	static EncodedGeometry fill(int minExtent) {
		int width = 4096 + 2 * minExtent;
		return new EncodedGeometry(GeometryType.POLYGON, new int[] {
			Command.MOVE_TO.value | (1 << 3), zigZagEncode(-minExtent), zigZagEncode(-minExtent),
			Command.LINE_TO.value | (3 << 3), zigZagEncode(width), 0, 0, zigZagEncode(width), zigZagEncode(-width), 0,
			Command.CLOSE_PATH.value | (1 << 3)
		});
	}

	/**
	 * Combines Planetiler's {@code encodeGeometry(geom, scale).unscale()} step so the
	 * integer extent coordinates passed to MLT match Planetiler exactly.
	 */
	private static int[] unscale(int[] commands, int scale, GeometryType geomType) {
		var result = new ArrayList<Integer>();
		int geometryCount = commands.length;
		int length = 0;
		int command = 0;
		int i = 0;
		int inX = 0;
		int inY = 0;
		int outX = 0;
		int outY = 0;
		int startX = 0;
		int startY = 0;
		double scaleFactor = Math.pow(2.0, -scale);
		int lengthIdx = 0;
		int moveToIdx = 0;
		int pointsInShape = 0;
		boolean first = true;
		while(i < geometryCount) {
			if(length <= 0) {
				length = commands[i++];
				lengthIdx = result.size();
				result.add(length);
				command = length & 7;
				length >>= 3;
			}
			if(length <= 0)
				continue;
			if(command == Command.MOVE_TO.value) {
				if(!first && pointsInShape < geomType.minPoints()) {
					int prevCommand = result.get(lengthIdx);
					truncate(result, moveToIdx);
					result.add(prevCommand);
					outX = startX;
					outY = startY;
				}
				pointsInShape = 0;
				startX = outX;
				startY = outY;
				moveToIdx = result.size() - 1;
			}
			first = false;
			if(command == Command.CLOSE_PATH.value) {
				++pointsInShape;
				--length;
				continue;
			}
			int dx = commands[i++];
			int dy = commands[i++];
			--length;
			dx = zigZagDecode(dx);
			dy = zigZagDecode(dy);
			int nextX = (int)Math.round((double)(inX += dx) * scaleFactor);
			int nextY = (int)Math.round((double)(inY += dy) * scaleFactor);
			if(nextX == outX && nextY == outY && command == Command.LINE_TO.value) {
				int commandLength = result.get(lengthIdx) - 8;
				if(commandLength < 8) {
					truncate(result, lengthIdx);
					continue;
				}
				result.set(lengthIdx, commandLength);
				continue;
			}
			++pointsInShape;
			result.add(zigZagEncode(nextX - outX));
			result.add(zigZagEncode(nextY - outY));
			outX = nextX;
			outY = nextY;
		}
		if(pointsInShape < geomType.minPoints())
			truncate(result, moveToIdx);
		int[] out = new int[result.size()];
		for(int j=0; j<out.length; j++)
			out[j] = result.get(j);
		return out;
	}

	private static void truncate(ArrayList<Integer> list, int size) {
		while(list.size() > size)
			list.remove(list.size()-1);
	}

	private static Encoded encode(Geometry geometry, int scale) {
		var encoder = new CommandEncoder(scale);
		encoder.accept(geometry);
		var type = GeometryType.typeOf(geometry);
		return new Encoded(encoder.result.stream().mapToInt(Integer::intValue).toArray(), type, scale);
	}

	private static Geometry decode(Encoded encoded) throws GeometryException {
		return decodeCommands(encoded.type, encoded.commands, 1 << encoded.scale);
	}

	private static int zigZagEncode(int n) {
		return n << 1 ^ n >> 31;
	}

	private static int zigZagDecode(int n) {
		return n >> 1 ^ -(n & 1);
	}

	private static Geometry decodeCommands(GeometryType geomType, int[] commands, double scale) throws GeometryException {
		try {
			var gf = GeoUtils.JTS_FACTORY;
			int x = 0;
			int y = 0;
			var allCoordSeqs = new ArrayList<MutableCoordinateSequence>();
			MutableCoordinateSequence currentCoordSeq = null;
			int geometryCount = commands.length;
			int length = 0;
			int command = 0;
			int i = 0;
			while(i < geometryCount) {
				if(length <= 0) {
					length = commands[i++];
					command = length & 7;
					length >>= 3;
					if(geomType != GeometryType.POINT || i == 1) {
						// expected
					} else {
						throw new GeometryException("invalid multipoint command");
					}
				}
				if(length <= 0)
					continue;
				if(command == Command.MOVE_TO.value) {
					currentCoordSeq = new MutableCoordinateSequence();
					allCoordSeqs.add(currentCoordSeq);
				}
				if(command == Command.CLOSE_PATH.value) {
					if(geomType != GeometryType.POINT && currentCoordSeq != null && !currentCoordSeq.isEmpty())
						currentCoordSeq.closeRing();
					--length;
					continue;
				}
				int dx = commands[i++];
				int dy = commands[i++];
				--length;
				dx = zigZagDecode(dx);
				dy = zigZagDecode(dy);
				currentCoordSeq.forceAddPoint((x += dx) / scale, (y += dy) / scale);
			}

			Geometry geometry;
			boolean outerCCW = false;
			switch(geomType) {
				case LINE -> {
					var lines = new ArrayList<LineString>(allCoordSeqs.size());
					for(var seq : allCoordSeqs)
						if(seq.size() > 1)
							lines.add(gf.createLineString(seq));
					geometry = lines.size() == 1 ? lines.getFirst() : gf.createMultiLineString(lines.toArray(LineString[]::new));
				}
				case POINT -> {
					var cs = new PackedCoordinateSequence.Double(allCoordSeqs.size(), 2, 0);
					for(int j=0; j<allCoordSeqs.size(); j++) {
						var seq = allCoordSeqs.get(j);
						cs.setOrdinate(j, 0, seq.getX(0));
						cs.setOrdinate(j, 1, seq.getY(0));
					}
					geometry = cs.size() == 1 ? gf.createPoint(cs) : gf.createMultiPoint(cs);
				}
				case POLYGON -> {
					var polygonRings = new ArrayList<List<LinearRing>>();
					var ringsForCurrentPolygon = new ArrayList<LinearRing>();
					boolean first = true;
					for(var seq : allCoordSeqs) {
						if(!ringsForCurrentPolygon.isEmpty() && seq.size() < 2)
							continue;
						LinearRing ring = gf.createLinearRing(seq);
						boolean ccw = Orientation.isCCW(seq);
						if(first) {
							first = false;
							outerCCW = ccw;
						}
						if(ccw == outerCCW) {
							ringsForCurrentPolygon = new ArrayList<>();
							polygonRings.add(ringsForCurrentPolygon);
						}
						ringsForCurrentPolygon.add(ring);
					}
					var polygons = new ArrayList<Polygon>();
					for(var rings : polygonRings) {
						var holes = rings.subList(1, rings.size()).toArray(LinearRing[]::new);
						polygons.add(gf.createPolygon(rings.getFirst(), holes));
					}
					geometry = polygons.size() == 1 ? polygons.getFirst() : gf.createMultiPolygon(polygons.toArray(Polygon[]::new));
				}
				default -> throw new GeometryException("unknown geometry type");
			}
			return geometry == null ? GeoUtils.EMPTY_GEOMETRY : geometry;
		} catch(IllegalArgumentException e) {
			throw new GeometryException("unable to decode geometry", e);
		}
	}

	private static final class CommandEncoder {
		final List<Integer> result = new ArrayList<>();
		private final double scale;
		int x = 0;
		int y = 0;

		CommandEncoder(int scale) {
			this.scale = (double)(4096 << scale) / 256.0;
		}

		static boolean shouldClosePath(Geometry geometry) {
			return geometry instanceof Polygon || geometry instanceof LinearRing;
		}

		static int commandAndLength(Command command, int repeat) {
			return repeat << 3 | command.value;
		}

		void accept(Geometry geometry) {
			if(geometry instanceof MultiLineString multiLineString) {
				for(int i=0; i<multiLineString.getNumGeometries(); i++)
					encode(((LineString)multiLineString.getGeometryN(i)).getCoordinateSequence(), false, GeometryType.LINE);
			} else if(geometry instanceof Polygon polygon) {
				encode(polygon.getExteriorRing().getCoordinateSequence(), true, GeometryType.POLYGON);
				for(int i=0; i<polygon.getNumInteriorRing(); i++)
					encode(polygon.getInteriorRingN(i).getCoordinateSequence(), true, GeometryType.LINE);
			} else if(geometry instanceof MultiPolygon multiPolygon) {
				for(int i=0; i<multiPolygon.getNumGeometries(); i++)
					accept(multiPolygon.getGeometryN(i));
			} else if(geometry instanceof LineString lineString) {
				encode(lineString.getCoordinateSequence(), shouldClosePath(geometry), GeometryType.LINE);
			} else if(geometry instanceof Point point) {
				encode(point.getCoordinateSequence(), false, GeometryType.POINT);
			} else if(geometry instanceof Puntal) {
				encode(new CoordinateArraySequence(geometry.getCoordinates()), shouldClosePath(geometry), geometry instanceof MultiPoint, GeometryType.POINT);
			}
		}

		void encode(CoordinateSequence cs, boolean closePathAtEnd, GeometryType geomType) {
			encode(cs, closePathAtEnd, false, geomType);
		}

		void encode(CoordinateSequence cs, boolean closePathAtEnd, boolean multiPoint, GeometryType geomType) {
			if(cs.size() == 0)
				throw new IllegalArgumentException("empty geometry");
			int startIdx = result.size();
			int numPoints = 0;
			int lineToIndex = 0;
			int lineToLength = 0;
			int startX = x;
			int startY = y;
			for(int i=0; i<cs.size(); i++) {
				double cx = cs.getX(i);
				double cy = cs.getY(i);
				if(i == 0)
					result.add(commandAndLength(Command.MOVE_TO, multiPoint ? cs.size() : 1));
				int px = (int)Math.round(cx * scale);
				int py = (int)Math.round(cy * scale);
				if(i > 0 && px == x && py == y && !multiPoint) {
					--lineToLength;
					continue;
				}
				if(closePathAtEnd && cs.size() > 1 && i == cs.size()-1 && cs.getX(0) == cx && cs.getY(0) == cy) {
					--lineToLength;
					continue;
				}
				result.add(zigZagEncode(px - x));
				result.add(zigZagEncode(py - y));
				++numPoints;
				x = px;
				y = py;
				if(i == 0 && cs.size() > 1 && !multiPoint) {
					lineToIndex = result.size();
					lineToLength = cs.size() - 1;
					result.add(commandAndLength(Command.LINE_TO, lineToLength));
				}
			}
			if(lineToIndex > 0) {
				if(lineToLength == 0)
					result.remove(lineToIndex);
				else
					result.set(lineToIndex, commandAndLength(Command.LINE_TO, lineToLength));
			}
			if(closePathAtEnd) {
				result.add(commandAndLength(Command.CLOSE_PATH, 1));
				++numPoints;
			}
			if(numPoints < geomType.minPoints()) {
				while(result.size() > startIdx)
					result.remove(result.size()-1);
				x = startX;
				y = startY;
			}
		}
	}
}
