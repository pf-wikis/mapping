package io.github.pfwikis.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Coordinate;

import io.github.pfwikis.tile.TileGeometryCodec.GeometryType;

class TileSignatureTrackerTest {

	@TempDir
	Path tempDir;

	@Test
	void dropsFillChildWithSameContentAsParent() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fill = List.of(fill(0, 0, 0));
			tracker.keptRanges(tileAt(1, 0), 1, fill);
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, fill)).isEmpty();
		}
	}

	@Test
	void fillSignatureIsOrderIndependent() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var a = fill(0, 0, 0);
			var b = fill(1, 3, 7);
			tracker.keptRanges(tileAt(1, 0), 1, List.of(a, b));
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, List.of(b, a))).isEmpty();
		}
	}

	@Test
	void keepsFillChildWhenAttributesChange() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(fill(0, 0, 0)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, List.of(fill(0, 0, 1)))).isNull();
		}
	}

	@Test
	void keepsFillChildWhenFeaturesAreAddedOrRemoved() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(fill(0, 0, 0)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, List.of(fill(0, 0, 0), fill(1, 0, 0)))).isNull();
		}
		try(var tracker = new TileSignatureTracker(tempDir.resolve("removed"))) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(fill(0, 0, 0), fill(1, 0, 0)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, List.of(fill(0, 0, 0)))).isNull();
		}
	}

	@Test
	void keepsFillChildWhenParentHasBoundaryGeometry() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(line(0, 0, 0)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, List.of(fill(0, 0, 0)))).isNull();
		}
	}

	@Test
	void keepsOnlyChildrenWhoseParentMatches() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var matching = List.of(fill(0, 0, 0));
			var other = List.of(fill(0, 0, 1));
			tracker.keptRanges(tileAt(1, 0), 1, matching);
			tracker.keptRanges(tileAt(1, 1), 1, other);
			// Children 2..3 are under parent 0 (matching) and 4..7 under parent 1.
			// Offsets are relative to the run start, so the kept part starts at 2.
			assertThat(tracker.keptRanges(tileAt(2, 2), 6, matching))
				.containsExactly(2, 4);
		}
	}

	@Test
	void keepsAFillRepresentationEveryNinthZoom() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fill = List.of(fill(0, 0, 0));
			tracker.keptRanges(tileAt(8, 0), 1, fill);
			assertThat(tracker.keptRanges(tileAt(9, 0), 4, fill)).isNull();
		}
	}

	@Test
	void dropsMatchingPoints() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(point(1000, 1200)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(point(2000, 2400)))).isEmpty();
		}
	}

	@Test
	void comparesPointsInTheCorrectChildQuadrant() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			// Child local 1 is the right half of the parent: x = 2*3000 - 4096.
			tracker.keptRanges(tileAt(1, 0), 1, List.of(point(3000, 1000)));
			assertThat(tracker.keptRanges(tileAt(2, 1), 1, List.of(point(1904, 2000)))).isEmpty();
		}
	}

	@Test
	void keepsPointsOutsideTheErrorMargin() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(point(1000, 1200)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(point(2010, 2400)))).isNull();
		}
	}

	@Test
	void keepsPointsWhenPropertiesChange() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(point(1000, 1200)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(point(0, 0, 1, 2000, 2400)))).isNull();
		}
	}

	@Test
	void dropsPointChainsAgainstTheFirstStoredAncestor() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(point(1000, 1000)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(point(2000, 2000)))).isEmpty();
			// The z1 reference overzooms to 4000,4000 even though z2 was dropped.
			assertThat(tracker.keptRanges(tileAt(3, 0), 1, List.of(point(4000, 4000)))).isEmpty();
		}
	}

	@Test
	void measuresChainErrorAgainstTheFirstStoredAncestor() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(point(1000, 1000)));
			// 2 units from the z1 reference: within the 0.1% margin.
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(point(2002, 2002)))).isEmpty();
			// 6 units from the z1 reference, even though it is only 2 from the
			// z2 tile's own position.
			assertThat(tracker.keptRanges(tileAt(3, 0), 1, List.of(point(4006, 4006)))).isNull();
		}
	}

	@Test
	void keepsFillChildWhenParentHasAPointInThatArea() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(fill(0, 1, 0), point(1, 0, 0, 1000, 1000)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(fill(0, 1, 0)))).isNull();
		}
	}

	@Test
	void ignoresParentPointsOutsideTheChildBuffer() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1, List.of(fill(0, 1, 0), point(1, 0, 0, 4000, 1000)));
			// Child 0 gets the point at overzoomed x=8000, outside its buffer.
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(fill(0, 1, 0)))).isEmpty();
			// Child 1 gets it at 8000-4096=3904, inside its buffer.
			assertThat(tracker.keptRanges(tileAt(2, 1), 1, List.of(fill(0, 1, 0)))).isNull();
		}
	}

	private static TileStore.TileFeature fill(int layer, long order, int attrs) {
		return new TileStore.TileFeature(layer, order, attrs, GeometryType.POLYGON, new int[0], true);
	}

	private static TileStore.TileFeature line(int layer, long order, int attrs) {
		return new TileStore.TileFeature(layer, order, attrs, GeometryType.LINE, new int[0], false);
	}

	private static TileStore.TileFeature point(int x, int y) {
		return point(0, 0, 0, x, y);
	}

	private static TileStore.TileFeature point(int layer, long order, int attrs, int x, int y) {
		var geometry = GeoUtils.JTS_FACTORY.createPoint(new Coordinate(x / 16d, y / 16d));
		var encoded = TileGeometryCodec.encodeToCommands(geometry, 0);
		return new TileStore.TileFeature(layer, order, attrs, encoded.type(), encoded.commands(), false);
	}

	private static long tileAt(int zoom, long local) {
		return TileCoord.startIndexForZoom(zoom) + local;
	}
}
