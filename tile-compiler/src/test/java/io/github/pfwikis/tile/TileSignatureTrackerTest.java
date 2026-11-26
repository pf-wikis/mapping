package io.github.pfwikis.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
	void keepsFillChildWhenParentHasBoundaryGeometryInThatArea() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fill = fill(0, 1, 0);
			tracker.keptRanges(tileAt(1, 0), 1,
				List.of(fill, line(0, 0, 0, 1000, 1000, 3000, 1000)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(fill))).isNull();
		}
	}

	@Test
	void dropsBoundaryChildWithinTolerance() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1,
				List.of(line(0, 0, 0, 500, 1000, 1500, 1000)));
			// Same line overzoomed, plus a collinear vertex that adds no shape.
			assertThat(tracker.keptRanges(tileAt(2, 0), 1,
				List.of(line(0, 0, 0, 1000, 2000, 2000, 2000, 3000, 2000)))).isEmpty();
		}
	}

	@Test
	void keepsBoundaryChildOutsideTolerance() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1,
				List.of(line(0, 0, 0, 500, 1000, 1500, 1000)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1,
				List.of(line(0, 0, 0, 1000, 2010, 3000, 2010)))).isNull();
		}
	}

	@Test
	void keepsBoundaryChildWithDifferentFeatureSet() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1,
				List.of(line(0, 0, 0, 500, 1000, 1500, 1000)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1,
				List.of(
					line(0, 0, 0, 1000, 2000, 3000, 2000),
					line(1, 0, 0, 1000, 2500, 3000, 2500)
				))).isNull();
		}
	}

	@Test
	void dropsBoundaryChainsAgainstTheFirstStoredAncestor() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			tracker.keptRanges(tileAt(1, 0), 1,
				List.of(line(0, 0, 0, 500, 500, 1000, 500)));
			assertThat(tracker.keptRanges(tileAt(2, 0), 1,
				List.of(line(0, 0, 0, 1000, 1000, 2000, 1000)))).isEmpty();
			assertThat(tracker.keptRanges(tileAt(3, 0), 1,
				List.of(line(0, 0, 0, 2000, 2000, 4000, 2000)))).isEmpty();
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

	@Test
	void reportsDropStatistics() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fillA = List.of(fill(0, 0, 0));
			var fillB = List.of(fill(0, 0, 1));
			tracker.keptRanges(tileAt(1, 0), 1, fillA);
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, fillA)).isEmpty();
			tracker.keptRanges(tileAt(3, 0), 16, fillB);

			var stats = tracker.dropStatistics();
			assertThat(stats.keptTiles()).isEqualTo(17);
			assertThat(stats.droppedTiles()).isEqualTo(4);
			assertThat(stats.droppedWithDroppedParent()).isZero();
			assertThat(stats.droppedWithDroppedAncestor()).isZero();
			assertThat(stats.droppedWithKeptChild()).isEqualTo(4);
		}
	}

	@Test
	void reportsDroppedAncestorsThroughChains() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fill = List.of(fill(0, 0, 0));
			tracker.keptRanges(tileAt(1, 0), 1, fill);
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, fill)).isEmpty();
			assertThat(tracker.keptRanges(tileAt(3, 0), 16, fill)).isEmpty();
			assertThat(tracker.keptRanges(tileAt(4, 0), 64, fill)).isEmpty();

			var stats = tracker.dropStatistics();
			assertThat(stats.keptTiles()).isEqualTo(1);
			assertThat(stats.droppedTiles()).isEqualTo(84);
			assertThat(stats.droppedWithDroppedParent()).isEqualTo(80);
			assertThat(stats.droppedWithDroppedAncestor()).isEqualTo(80);
			assertThat(stats.droppedWithKeptChild()).isZero();
		}
	}

	@Test
	void reportsDroppedAncestorBehindAKeptParent() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fillA = List.of(fill(0, 0, 0));
			var fillB = List.of(fill(0, 0, 1));
			tracker.keptRanges(tileAt(1, 0), 1, fillA);
			assertThat(tracker.keptRanges(tileAt(2, 0), 4, fillA)).isEmpty();
			tracker.keptRanges(tileAt(3, 0), 16, fillB);
			assertThat(tracker.keptRanges(tileAt(4, 0), 64, fillB)).isEmpty();

			var stats = tracker.dropStatistics();
			assertThat(stats.keptTiles()).isEqualTo(17);
			assertThat(stats.droppedTiles()).isEqualTo(68);
			// The z4 parent is kept; only the z2 grandparent was dropped.
			assertThat(stats.droppedWithDroppedParent()).isZero();
			assertThat(stats.droppedWithDroppedAncestor()).isEqualTo(64);
			assertThat(stats.droppedWithKeptChild()).isEqualTo(4);
		}
	}

	@Test
	void dropsFillRunCoveredByOneMultiParentEntry() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fill = List.of(fill(0, 0, 0));
			assertThat(tracker.keptRanges(tileAt(2, 0), 8, fill)).isNull();
			assertThat(tracker.keptRanges(tileAt(3, 0), 32, fill)).isEmpty();
		}
	}

	@Test
	void keepsOnlyThePartOfAFillRunWhoseParentEntryMatches() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fillA = List.of(fill(0, 0, 0));
			var fillB = List.of(fill(0, 0, 1));
			tracker.keptRanges(tileAt(2, 0), 4, fillA);
			tracker.keptRanges(tileAt(2, 4), 4, fillB);
			// Parents 0..3 match, parents 4..7 do not; the run starts in parent 0.
			assertThat(tracker.keptRanges(tileAt(3, 2), 30, fillA))
				.containsExactly(14, 16);
		}
	}

	@Test
	void keepsFillChildrenWhereThePreviousZoomHasNoEntry() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fill = List.of(fill(0, 0, 0));
			tracker.keptRanges(tileAt(2, 0), 4, fill);
			// Parents 0..3 have an entry, parents 4..7 do not.
			assertThat(tracker.keptRanges(tileAt(3, 0), 32, fill))
				.containsExactly(16, 16);
		}
	}

	@Test
	void reportsKeptChildrenAcrossLargeFillRuns() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fillA = List.of(fill(0, 0, 0));
			var fillB = List.of(fill(0, 0, 1));
			tracker.keptRanges(tileAt(1, 0), 2, fillA);
			assertThat(tracker.keptRanges(tileAt(2, 0), 8, fillA)).isEmpty();
			tracker.keptRanges(tileAt(3, 0), 32, fillB);

			var stats = tracker.dropStatistics();
			assertThat(stats.keptTiles()).isEqualTo(34);
			assertThat(stats.droppedTiles()).isEqualTo(8);
			assertThat(stats.droppedWithKeptChild()).isEqualTo(8);
		}
	}

	@Test
	void ignoresParentBoundaryThatClipsAway() throws Exception {
		try(var tracker = new TileSignatureTracker(tempDir)) {
			var fill = fill(0, 1, 0);
			tracker.keptRanges(tileAt(1, 0), 1,
				List.of(fill, line(0, 0, 0, 3500, 3500, 3800, 3800)));
			// Child 0 is the top-left quadrant; the parent line clips away there.
			assertThat(tracker.keptRanges(tileAt(2, 0), 1, List.of(fill))).isEmpty();
		}
	}

	@Test
	void batchSubmissionMatchesSynchronousProcessing() throws Exception {
		assertThat(processBatch(true)).containsExactly(processBatch(false));
	}

	private long[] processBatch(boolean batch) throws Exception {
		var directory = Files.createTempDirectory("sig-batch-");
		try(var tracker = new TileSignatureTracker(directory)) {
			for(int i=0; i<16; i++)
				tracker.keptRanges(tileAt(1, i), 1, features(i));

			var jobs = new ArrayList<TileSignatureTracker.SignatureJob>();
			var kept = new long[64][];
			for(int j=0; j<64; j++) {
				var content = features(j + 16);
				if(batch)
					jobs.add(tracker.submit(tileAt(2, j), 1, content));
				else
					kept[j] = encode(tracker.keptRanges(tileAt(2, j), 1, content));
			}
			if(batch) {
				for(int j=0; j<64; j++)
					kept[j] = encode(tracker.complete(jobs.get(j)));
			}

			var flat = new long[64];
			for(int j=0; j<64; j++)
				flat[j] = kept[j].length == 0 ? -2 : kept[j][0];
			return flat;
		}
	}

	private static List<TileStore.TileFeature> features(int seed) {
		return List.of(
			fill(seed % 3, seed, seed % 2),
			line(seed % 2, seed, seed % 4,
				200 + (seed * 37) % 900, 300 + (seed * 53) % 900,
				3200 - (seed * 29) % 900, 3600 - (seed * 41) % 900));
	}

	private static long[] encode(long[] kept) {
		return kept == null ? new long[] {-1} : kept;
	}

	private static TileStore.TileFeature fill(int layer, long order, int attrs) {
		return new TileStore.TileFeature(layer, order, attrs, GeometryType.POLYGON, new int[0], true);
	}

	private static TileStore.TileFeature line(int layer, long order, int attrs, double... xy) {
		var coordinates = new Coordinate[xy.length / 2];
		for(int i=0; i<coordinates.length; i++)
			coordinates[i] = new Coordinate(xy[2*i] / 16d, xy[2*i+1] / 16d);
		var geometry = GeoUtils.JTS_FACTORY.createLineString(coordinates);
		var encoded = TileGeometryCodec.encodeToCommands(geometry, 0);
		return new TileStore.TileFeature(layer, order, attrs, encoded.type(), encoded.commands(), false);
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
