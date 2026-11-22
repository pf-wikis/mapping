/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.jcip.annotations.NotThreadSafe
 *  org.roaringbitmap.PeekableIntIterator
 *  org.roaringbitmap.RoaringBitmap
 */
package io.github.pfwikis.tile;

import java.util.PrimitiveIterator;
import org.roaringbitmap.PeekableIntIterator;
import org.roaringbitmap.RoaringBitmap;

public class IntRangeSet
implements Iterable<Integer> {
    private final RoaringBitmap bitmap = new RoaringBitmap();

    public IntRangeSet addAll(IntRangeSet other) {
        this.bitmap.or(other.bitmap);
        return this;
    }

    public IntRangeSet removeAll(IntRangeSet other) {
        this.bitmap.andNot(other.bitmap);
        return this;
    }

    public PrimitiveIterator.OfInt iterator() {
        return new Iter(this.bitmap.getIntIterator());
    }

    public IntRangeSet add(int a, int b) {
        this.bitmap.add((long)a, (long)b + 1L);
        return this;
    }

    public IntRangeSet remove(int a, int b) {
        this.bitmap.remove((long)a, (long)b + 1L);
        return this;
    }

    public IntRangeSet remove(int a) {
        this.bitmap.remove(a);
        return this;
    }

    public boolean contains(int y) {
        return this.bitmap.contains(y);
    }

    public RoaringBitmap bitmap() {
        return this.bitmap;
    }

    public IntRangeSet intersect(IntRangeSet other) {
        this.bitmap.and(other.bitmap);
        return this;
    }

    public String toString() {
        return this.bitmap.toString();
    }

    public boolean isEmpty() {
        return this.bitmap.isEmpty();
    }

    public IntRangeSet xor(int start, int end) {
        this.bitmap.xor(RoaringBitmap.bitmapOfRange((long)start, (long)((long)end + 1L)));
        return this;
    }

    private record Iter(PeekableIntIterator iter) implements PrimitiveIterator.OfInt
    {
        @Override
        public boolean hasNext() {
            return this.iter.hasNext();
        }

        @Override
        public int nextInt() {
            return this.iter.next();
        }
    }
}

