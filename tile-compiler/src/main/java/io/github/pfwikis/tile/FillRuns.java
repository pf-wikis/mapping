package io.github.pfwikis.tile;

import java.util.Arrays;
import java.util.Map;

import com.carrotsearch.hppc.LongArrayList;

/**
 * Converts the settled interior of a polygon (a set of filled tile rows) into
 * contiguous Hilbert-id runs.
 *
 * <p>The naive approach iterates every filled tile and sorts the resulting ids.
 * At zoom 15 a world-spanning polygon has hundreds of millions of filled tiles,
 * which made this the dominant tiling cost. The Hilbert curve visits every
 * aligned {@code 64 x 64} square of tiles as one contiguous, aligned run of
 * {@code 4096} ids. Filled areas are therefore partitioned into aligned
 * blocks:
 *
 * <ul>
 *   <li>completely filled blocks become a single run without touching their
 *       individual tiles;</li>
 *   <li>only blocks that are partially filled or contain a boundary tile are
 *       expanded tile by tile and sorted.</li>
 * </ul>
 *
 * <p>Most of a continent or ocean interior is emitted in one operation per
 * block, while detailed work stays proportional to the polygon boundary rather
 * than its area.
 */
final class FillRuns {
	/** Block side in tiles. Blocks are squares aligned to the world grid. */
	private static final int BLOCK_SIZE = 64;

	private FillRuns() {}

	/**
	 * @param fills settled interior y-ranges per tile column
	 * @param boundaries tiles that already have boundary geometry (fills are
	 *        suppressed there, exactly like {@link TiledGeometry#getFilledTiles()})
	 * @return sorted, non-overlapping {@code (start, length)} pairs
	 */
	static long[] collect(Map<Integer, IntRangeSet> fills, Map<Integer, IntRangeSet> boundaries, int zoom) {
		if(fills == null || fills.isEmpty())
			return new long[0];

		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for(var entry : fills.entrySet()) {
			var ys = entry.getValue();
			if(ys == null || ys.isEmpty())
				continue;
			minX = Math.min(minX, entry.getKey());
			maxX = Math.max(maxX, entry.getKey());
			minY = Math.min(minY, ys.bitmap().first());
			maxY = Math.max(maxY, ys.bitmap().last());
		}
		if(minX > maxX)
			return new long[0];

		int world = 1 << zoom;
		int block = Math.min(BLOCK_SIZE, world);
		int startX = minX & ~(block - 1);
		int startY = minY & ~(block - 1);

		var runs = new LongArrayList();
		var partialIds = new LongArrayList();

		for(int by = startY; by <= maxY; by += block) {
			int endY = Math.min(world, by + block);
			for(int bx = startX; bx <= maxX; bx += block) {
				int endX = Math.min(world, bx + block);
				boolean full = true;
				boolean any = false;
				boolean hasBoundary = false;
				for(int x = bx; x < endX; x++) {
					var boundary = boundaries.get(x);
					if(boundary != null && boundary.bitmap().nextValue(by) < endY)
						hasBoundary = true;

					var fill = fills.get(x);
					if(fill == null) {
						full = false;
						continue;
					}
					if(fill.bitmap().contains((long)by, (long)endY)) {
						any = true;
					} else {
						full = false;
						if(!any && fill.bitmap().nextValue(by) < endY)
							any = true;
					}
				}

				if(full && !hasBoundary) {
					long size = (long)(endX - bx) * (endY - by);
					long start = TileCoord.hilbertBlockStart(bx, by, zoom, size);
					runs.add(start);
					runs.add(size);
				} else if(any) {
					for(int x = bx; x < endX; x++) {
						var fill = fills.get(x);
						if(fill == null)
							continue;
						var boundary = boundaries.get(x);
						for(int y = by; y < endY; y++) {
							if(!fill.bitmap().contains(y))
								continue;
							if(boundary != null && boundary.bitmap().contains(y))
								continue;
							partialIds.add(TileCoord.hilbertEncoded(x, y, zoom));
						}
					}
				}
			}
		}

		Arrays.sort(partialIds.buffer, 0, partialIds.size());
		long previous = -2;
		for(int i = 0; i < partialIds.size(); i++) {
			long id = partialIds.buffer[i];
			if(id == previous + 1) {
				runs.set(runs.size() - 1, runs.get(runs.size() - 1) + 1);
			} else {
				runs.add(id);
				runs.add(1L);
			}
			previous = id;
		}
		return normalize(runs);
	}

	private static long[] normalize(LongArrayList runs) {
		int count = runs.size() / 2;
		if(count == 0)
			return new long[0];
		var pairs = Arrays.copyOf(runs.buffer, count * 2);
		sortPairs(pairs, 0, count - 1);

		var result = new long[count * 2];
		int out = 0;
		long currentStart = 0;
		long currentEnd = 0;
		boolean hasCurrent = false;
		for(int i = 0; i < count; i++) {
			long start = pairs[2 * i];
			long end = start + pairs[2 * i + 1];
			if(!hasCurrent) {
				currentStart = start;
				currentEnd = end;
				hasCurrent = true;
			} else if(start <= currentEnd) {
				currentEnd = Math.max(currentEnd, end);
			} else {
				result[2 * out] = currentStart;
				result[2 * out + 1] = currentEnd - currentStart;
				out++;
				currentStart = start;
				currentEnd = end;
			}
		}
		if(hasCurrent) {
			result[2 * out] = currentStart;
			result[2 * out + 1] = currentEnd - currentStart;
			out++;
		}
		return Arrays.copyOf(result, out * 2);
	}

	private static void sortPairs(long[] pairs, int lo, int hi) {
		while(lo < hi) {
			if(hi - lo < 16) {
				insertionSort(pairs, lo, hi);
				return;
			}
			long pivot = pairs[2 * ((lo + hi) >>> 1)];
			int i = lo;
			int j = hi;
			while(i <= j) {
				while(pairs[2 * i] < pivot)
					i++;
				while(pairs[2 * j] > pivot)
					j--;
				if(i <= j) {
					swap(pairs, i, j);
					i++;
					j--;
				}
			}
			if(j - lo < hi - i) {
				sortPairs(pairs, lo, j);
				lo = i;
			} else {
				sortPairs(pairs, i, hi);
				hi = j;
			}
		}
	}

	private static void insertionSort(long[] pairs, int lo, int hi) {
		for(int i = lo + 1; i <= hi; i++) {
			int j = i;
			while(j > lo && pairs[2 * j] < pairs[2 * (j - 1)]) {
				swap(pairs, j, j - 1);
				j--;
			}
		}
	}

	private static void swap(long[] pairs, int a, int b) {
		long start = pairs[2 * a];
		long length = pairs[2 * a + 1];
		pairs[2 * a] = pairs[2 * b];
		pairs[2 * a + 1] = pairs[2 * b + 1];
		pairs[2 * b] = start;
		pairs[2 * b + 1] = length;
	}
}
