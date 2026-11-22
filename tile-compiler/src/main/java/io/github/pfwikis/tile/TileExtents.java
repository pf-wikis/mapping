package io.github.pfwikis.tile;

/**
 * The compiler always uses world bounds, so every tile in the zoom is in range.
 */
final class TileExtents {

	private final int maxZoom;

	TileExtents(int maxZoom) {
		this.maxZoom = maxZoom;
	}

	ForZoom getForZoom(int zoom) {
		return new ForZoom(zoom);
	}

	record ForZoom(int z) implements TilePredicate {
		int minX() {
			return 0;
		}

		int minY() {
			return 0;
		}

		int maxX() {
			return 1 << z;
		}

		int maxY() {
			return 1 << z;
		}

		boolean testX(int x) {
			return x >= minX() && x < maxX();
		}

		boolean testY(int y) {
			return y >= minY() && y < maxY();
		}

		@Override
		public boolean test(int x, int y) {
			return testX(x) && testY(y);
		}
	}
}
