package io.github.pfwikis.layercompiler.steps.rivers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;
import org.locationtech.jts.math.Vector2D;

import com.beust.jcommander.internal.Lists;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.run.Tools;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import mil.nga.sf.geojson.Feature;
import mil.nga.sf.geojson.FeatureCollection;
import mil.nga.sf.geojson.LineString;
import mil.nga.sf.geojson.MultiLineString;
import mil.nga.sf.geojson.Point;
import mil.nga.sf.geojson.Polygon;

@Setter
@Slf4j
public class ShapeRivers extends LCStep {
	

	@Override
	public LCContent process() throws IOException {
		var result = new FeatureCollection();

		var rivers = collectRivers(getInput());
		markSprings(ctx, getInput(), rivers);
		interpolateWidth(rivers);
		log.info("Processing " + rivers.size() + " river points");
		drawShapes(rivers, result);

		var cleaned = Tools.mapshaper(this, LCContent.from(result), "--filter-fields", "-clean", "sliver-control=0", "-dissolve2", "-explode");
		/*log.info(
			"Shaped rivers\nfrom {}\nto\n{}",
			getInput().toTmpFile(),
			cleaned.toTmpFile()
		);*/
		return cleaned;
	}

	private Collection<RPoint> collectRivers(LCContent in) throws IOException {
		var rivers = new HashMap<Vector2D, RPoint>();
		var featureCol = in.toNgaFeatureCollection();
		for (var feature : featureCol.getFeatures()) {
			double defaultWidth = Objects.requireNonNullElse((Number) feature.getProperties().get("width"), 2000).doubleValue();
			if (feature.getGeometry() instanceof LineString line) {
				collectRivers(defaultWidth, line.getLineString().getPoints(), rivers);
			} else if(feature.getGeometry() instanceof MultiLineString lines) {
				lines.getMultiLineString().getLineStrings().forEach(ls->
					collectRivers(defaultWidth, ls.getPoints(), rivers)
				);
			} else {
				throw new IllegalStateException("Unhandled type " + feature.getGeometryType());
			}
		}
		return rivers.values();
	}

	private void collectRivers(double defaultWidth, List<mil.nga.sf.Point> points, HashMap<Vector2D, RPoint> rivers) {
		for (int i = 0; i < points.size() - 1; i++) {
			var a = RPoint.v(points.get(i));
			var b = RPoint.v(points.get(i + 1));
			var pA = rivers.computeIfAbsent(a, RPoint::new);
			var pB = rivers.computeIfAbsent(b, RPoint::new);
			pA.getNeighbors().add(pB);
			pB.getNeighbors().add(pA);
			pA.setWidth(Math.max(metersToDeg(defaultWidth, points.get(i).getY()), pA.getWidth()));
			pB.setWidth(Math.max(metersToDeg(defaultWidth, points.get(i + 1).getY()), pB.getWidth()));

			if (i == 0)
				pA.setSegmentEnd(true);
			if (i == points.size() - 2)
				pB.setSegmentEnd(true);
		}
	}

	private double metersToDeg(double meters, double lat) {
		return meters * ((1. + 0.00001120378 * (Math.cos(2 * lat / 180 * Math.PI) - 1)) / Math.cos(lat / 180 * Math.PI) / 111319.491 / 2d);
	}

	private void markSprings(Ctx ctx, LCContent riversIn, Collection<RPoint> rivers) throws IOException {
		var landPoints = PointsOnLandSelector
				.collectLandPoints(this, ctx, riversIn, getInput("land_without_water"))
				.stream()
				.map(RPoint::v)
				.collect(Collectors.toSet());

		for (var p : rivers) {
			if (p.getNeighbors().size() == 1 && landPoints.contains(p.getLocation())) {
				p.setSpring(true);
			}
		}

	}

	private void interpolateWidth(Collection<RPoint> points) {
		for (var p : points) {
			if (p.getNeighbors().size() != 2 || p.isSegmentEnd()) {
				p.setInterpolated(true);
			}
		}
		var pointsWithStartValue = points.stream().filter(p -> p.isInterpolated()).toList();
		for (var p : pointsWithStartValue) {
			for (var n : p.getNeighbors()) {
				if (!n.isInterpolated()) {
					interpolateWidthChain(Lists.newArrayList(p, n));
				}
			}
		}
	}

	private void interpolateWidthChain(List<RPoint> chain) {
		var beforeLast = chain.get(chain.size() - 2);
		var last = chain.get(chain.size() - 1);
		for (var n : last.getNeighbors()) {

			if (n != beforeLast) {
				var newChain = Lists.newArrayList(chain);
				newChain.add(n);
				if (n.isInterpolated())
					interpolateWidthFinishChain(newChain);
				else
					interpolateWidthChain(newChain);
			}
		}
	}

	private void interpolateWidthFinishChain(List<RPoint> chain) {
		var first = chain.get(0);
		var mid = chain.get(chain.size() / 2);
		var last = chain.get(chain.size() - 1);

		double geoLength = 0;
		for (int i = 0; i < chain.size() - 1; i++) {
			geoLength += chain.get(i).distanceTo(chain.get(i + 1));
		}

		var interpX = new double[] { 0, geoLength / 2, geoLength };
		var interpY = new double[] { first.getWidth(), mid.getWidth(), last.getWidth() };

		// set width to zero at a spring
		// set midpoint to max 50km or 3/4ths from spring
		if (first.isSpring()) {
			interpY[0] = first.getWidth()/10;
			interpX[1] = geoLength * 3 / 4;
		}
		if (last.isSpring()) {
			interpY[2] = last.getWidth()/10;
			interpX[1] = geoLength * 1 / 4;
		}
		if (last.isSpring() && first.isSpring()) {
			log.error("There is a river segment with two springs between " + first + " and " + last);
			interpX[1] = .5 * geoLength;
		}

		// set scaling up to junction to only happen at short range
		if (first.getNeighbors().size() > 2 && first.getWidth() > mid.getWidth()) {
			interpX = ArrayUtils.insert(1, interpX, Math.min(1, geoLength / 4 - 1));
			interpY = ArrayUtils.insert(1, interpY, mid.getWidth());
		}
		if (last.getNeighbors().size() > 2 && last.getWidth() > mid.getWidth()) {
			interpX = ArrayUtils.insert(interpX.length - 1, interpX, Math.max(geoLength - 1, geoLength * 3 / 4 + 1));
			interpY = ArrayUtils.insert(interpY.length - 1, interpY, mid.getWidth());
		}

		var interpolate = new SmoothInterpolator(interpX, interpY);

		double lengthSum = 0;
		for (int i = 0; i < chain.size(); i++) {
			var width = interpolate.value(lengthSum);
			chain.get(i).setWidth(width);
			if (i < chain.size() - 1) {
				lengthSum += chain.get(i).distanceTo(chain.get(i + 1));
				chain.get(i).setInterpolated(true);
			}
		}
	}

	private void drawShapes(Collection<RPoint> riverPoints, FeatureCollection resultCollector) throws IOException {
		var chains = new ArrayList<List<RPoint>>();
		// collect nonsplitting rivers
		var openRiverPoints = new TreeSet<>(riverPoints);
		openRiverPoints.removeIf(p -> p.getNeighbors().size() > 2);
		while (!openRiverPoints.isEmpty()) {
			var a = openRiverPoints.pollFirst();
			var chain = new ArrayList<RPoint>();
			chain.add(a);

			chain.addAll(collectChain(a, a.getNeighbors().get(0)));
			if (a.getNeighbors().size() == 2) {
				var otherDirection = collectChain(a, a.getNeighbors().get(1));
				Collections.reverse(otherDirection);
				chain.addAll(0, otherDirection);
			}
			openRiverPoints.removeAll(chain);
			chains.add(chain);
		}

		// draw all linear rivers
		for (var chain : chains) {
			drawSimpleSection(chain, resultCollector);
		}

		// draw all crossings
		for (var p : riverPoints) {
			if (p.getNeighbors().size() > 2) {
				drawCrossing(p, resultCollector);
			}
		}
	}

	private List<RPoint> collectChain(RPoint last, RPoint current) {
		switch (current.getNeighbors().size()) {
			case 1:
				return Lists.newLinkedList(List.of(current));
			case 2:
				var next = current.getNeighbors().get(0);
				if (next == last)
					next = current.getNeighbors().get(1);
				var result = collectChain(current, next);
				result.add(0, current);
				return result;
			default:
				return Lists.newLinkedList(List.of(current));
		}
	}

	private void drawSimpleSection(List<RPoint> chain, FeatureCollection resultCollector) throws IOException {
		if (chain.size() < 3) {
			log.warn("very short river chain around " + chain.get(0));
			return;
		}
		var points = new ArrayList<Point>();

		for (int i = 0; i < chain.size() - 2; i++) {
			drawSimpleSection(chain.get(i), chain.get(i + 1), chain.get(i + 2), points);
		}
		drawSimpleCap(chain.get(chain.size() - 2), chain.get(chain.size() - 1), points);
		for (int i = chain.size() - 1; i > 1; i--) {
			drawSimpleSection(chain.get(i), chain.get(i - 1), chain.get(i - 2), points);
		}
		drawSimpleCap(chain.get(1), chain.get(0), points);

		resultCollector.addFeature(new Feature(new Polygon(List.of(new LineString(points)))));
	}

	private void drawSimpleSection(RPoint a, RPoint b, RPoint c, ArrayList<Point> points) {
		var ab = b.getLocation().subtract(a.getLocation()).normalize();
		var bc = c.getLocation().subtract(b.getLocation()).normalize();

		var dir1 = new Vector2D(-ab.getY(), ab.getX()).normalize().multiply(b.getWidth());
		var dir2 = new Vector2D(-bc.getY(), bc.getX()).normalize().multiply(b.getWidth());
		var dir = dir1.add(dir2).normalize().multiply(b.getWidth());
		if (ab.angleTo(bc) < -.075*Math.PI) {
			points.add(RPoint.p(b.getLocation().add(dir1)));
			points.add(RPoint.p(b.getLocation().add(dir)));
			points.add(RPoint.p(b.getLocation().add(dir2)));
		} else {
			points.add(RPoint.p(b.getLocation().add(dir)));
		}
	}

	private void drawCrossing(RPoint p, FeatureCollection resultCollector) throws IOException {
		var l = p.getNeighbors();
		for (int i = 0; i < l.size(); i++) {
			for (int j = i + 1; j < l.size(); j++) {
				var a = l.get(i);
				var b = l.get(i + 1);
				var list = Lists.newArrayList(a, p, b);

				var beforeA = findNext(p, a);
				if (beforeA != null)
					list.add(0, beforeA);
				var beforeB = findNext(p, b);
				if (beforeB != null)
					list.add(beforeB);

				drawSimpleSection(list, resultCollector);
			}
		}
	}

	private RPoint findNext(RPoint a, RPoint b) {
		switch (b.getNeighbors().size()) {
			case 1:
				return null;
			default:
				var next = b.getNeighbors().get(0);
				if (next == a)
					next = b.getNeighbors().get(1);
				return next;
		}
	}
	
	private static final double[] SPRING_CAP = {-1,-.8,-.6,-.4,-.2,0,.2,.4,.6,.8,1};
	private static final double[] SEA_CAP = {-1,1};

	private void drawSimpleCap(RPoint a, RPoint b, ArrayList<Point> points) {
		// round cap
		if (b.getNeighbors().size() == 1) {
			var ab = b.getLocation().subtract(a.getLocation()).normalize();
			for (double i : b.isSpring()?SPRING_CAP:SEA_CAP) {
				points.add(RPoint.p(b.getLocation().add(ab.rotate(-Math.PI * i / 2).multiply(b.getWidth()))));
			}
		} else {
			points.add(RPoint.p(b.getLocation()));
		}
	}
}
