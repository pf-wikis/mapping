package io.github.pfwikis.tile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
	void signatureIsOrderIndependentAndIgnoresBoundaryFeatures() {
		var a = new TileStore.TileFeature(0, 0, 0, GeometryType.POLYGON, new int[0], true);
		var b = new TileStore.TileFeature(1, 3, 7, GeometryType.POLYGON, new int[0], true);
		assertThat(TileSignatureTracker.signature(List.of(a, b)))
			.isEqualTo(TileSignatureTracker.signature(List.of(b, a)));
		assertThat(TileSignatureTracker.signature(List.of(a, line(2, 0, 0)))).isNull();
	}

	private static TileStore.TileFeature fill(int layer, long order, int attrs) {
		return new TileStore.TileFeature(layer, order, attrs, GeometryType.POLYGON, new int[0], true);
	}

	private static TileStore.TileFeature line(int layer, long order, int attrs) {
		return new TileStore.TileFeature(layer, order, attrs, GeometryType.LINE, new int[0], false);
	}

	private static long tileAt(int zoom, long local) {
		return TileCoord.startIndexForZoom(zoom) + local;
	}
}
