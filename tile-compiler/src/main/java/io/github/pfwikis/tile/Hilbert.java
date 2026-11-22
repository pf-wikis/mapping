/*
 * Decompiled with CFR 0.152.
 */
package io.github.pfwikis.tile;

public class Hilbert {
    private Hilbert() {
        throw new IllegalStateException("Utility class");
    }

    private static int deinterleave(int tx) {
        tx &= 0x55555555;
        tx = (tx | tx >>> 1) & 0x33333333;
        tx = (tx | tx >>> 2) & 0xF0F0F0F;
        tx = (tx | tx >>> 4) & 0xFF00FF;
        tx = (tx | tx >>> 8) & 0xFFFF;
        return tx;
    }

    private static int interleave(int tx) {
        tx = (tx | tx << 8) & 0xFF00FF;
        tx = (tx | tx << 4) & 0xF0F0F0F;
        tx = (tx | tx << 2) & 0x33333333;
        tx = (tx | tx << 1) & 0x55555555;
        return tx;
    }

    private static int prefixScan(int tx) {
        tx = tx >>> 8 ^ tx;
        tx = tx >>> 4 ^ tx;
        tx = tx >>> 2 ^ tx;
        tx = tx >>> 1 ^ tx;
        return tx;
    }

    public static int extractX(long xy) {
        return (int)(xy >>> 32);
    }

    public static int extractY(long xy) {
        return (int)xy;
    }

    public static long hilbertPositionToXY(int level, long pos) {
        int ipos = (int)(pos << 32 - 2 * level);
        int i0 = Hilbert.deinterleave(ipos);
        int i1 = Hilbert.deinterleave(ipos >>> 1);
        int t0 = (i0 | i1) ^ 0xFFFF;
        int t1 = i0 & i1;
        int prefixT0 = Hilbert.prefixScan(t0);
        int prefixT1 = Hilbert.prefixScan(t1);
        int a = (i0 ^ 0xFFFF) & prefixT1 | i0 & prefixT0;
        int resultX = (a ^ i1) >>> 16 - level;
        int resultY = (a ^ i0 ^ i1) >>> 16 - level;
        return (long)resultX << 32 | (long)resultY;
    }

    public static long hilbertXYToIndex(int level, int x, int y) {
        int a1 = (x <<= 16 - level) ^ (y <<= 16 - level);
        int b1 = 0xFFFF ^ a1;
        int c1 = 0xFFFF ^ (x | y);
        int d1 = x & (y ^ 0xFFFF);
        int hA = a1 | b1 >>> 1;
        int hB = a1 >>> 1 ^ a1;
        int hC = c1 >>> 1 ^ b1 & d1 >>> 1 ^ c1;
        int hD = a1 & c1 >>> 1 ^ d1 >>> 1 ^ d1;
        int a2 = hA;
        int b2 = hB;
        int c2 = hC;
        int d2 = hD;
        hA = a2 & a2 >>> 2 ^ b2 & b2 >>> 2;
        hB = a2 & b2 >>> 2 ^ b2 & (a2 ^ b2) >>> 2;
        int a3 = hA;
        int b3 = hB;
        int c3 = hC ^= a2 & c2 >>> 2 ^ b2 & d2 >>> 2;
        int d3 = hD ^= b2 & c2 >>> 2 ^ (a2 ^ b2) & d2 >>> 2;
        hA = a3 & a3 >>> 4 ^ b3 & b3 >>> 4;
        hB = a3 & b3 >>> 4 ^ b3 & (a3 ^ b3) >>> 4;
        int a4 = hA;
        int b4 = hB;
        int c4 = hC ^= a3 & c3 >>> 4 ^ b3 & d3 >>> 4;
        int d4 = hD ^= b3 & c3 >>> 4 ^ (a3 ^ b3) & d3 >>> 4;
        int a = (hC ^= a4 & c4 >>> 8 ^ b4 & d4 >>> 8) ^ hC >>> 1;
        int b = (hD ^= b4 & c4 >>> 8 ^ (a4 ^ b4) & d4 >>> 8) ^ hD >>> 1;
        int i0 = x ^ y;
        int i1 = b | 0xFFFF ^ (i0 | a);
        return Integer.toUnsignedLong((Hilbert.interleave(i1) << 1 | Hilbert.interleave(i0)) >>> 32 - 2 * level);
    }
}

