/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.carrotsearch.hppc.DoubleArrayList
 *  org.locationtech.jts.geom.Coordinate
 *  org.locationtech.jts.geom.CoordinateXY
 *  org.locationtech.jts.geom.Envelope
 *  org.locationtech.jts.geom.impl.PackedCoordinateSequence
 *  org.locationtech.jts.geom.impl.PackedCoordinateSequence$Double
 */
package io.github.pfwikis.tile;

import com.carrotsearch.hppc.DoubleArrayList;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

public class MutableCoordinateSequence
extends PackedCoordinateSequence {
    private final DoubleArrayList points;

    public MutableCoordinateSequence() {
        this(2);
    }

    public MutableCoordinateSequence(int size) {
        super(2, 0);
        this.points = new DoubleArrayList(2 * size);
    }

    public static MutableCoordinateSequence newScalingSequence(double relX, double relY, double scale) {
        return new ScalingSequence(scale, relX, relY);
    }

    public double getOrdinate(int index, int ordinateIndex) {
        return this.points.get(index * 2 + ordinateIndex);
    }

    public int size() {
        return this.points.size() >> 1;
    }

    protected Coordinate getCoordinateInternal(int index) {
        return new CoordinateXY(this.getX(index), this.getY(index));
    }

    @Deprecated
    public Object clone() {
        throw new UnsupportedOperationException();
    }

    public PackedCoordinateSequence copy() {
        return new PackedCoordinateSequence.Double(this.points.toArray(), this.dimension, this.measures);
    }

    public void setOrdinate(int index, int ordinate, double value) {
        this.points.set((index << 1) + ordinate, value);
    }

    public Envelope expandEnvelope(Envelope env) {
        for (int i = 0; i < this.points.size(); i += this.dimension) {
            env.expandToInclude(this.points.get(i), this.points.get(i + 1));
        }
        return env;
    }

    public void addPoint(double x, double y) {
        int size = this.size();
        if (size == 0 || this.getX(size - 1) != x || this.getY(size - 1) != y) {
            this.points.add(x, y);
        }
    }

    public void forceAddPoint(double x, double y) {
        this.points.add(x, y);
    }

    public void closeRing() {
        int size = this.size();
        if (size >= 1) {
            double firstX = this.getX(0);
            double firstY = this.getY(0);
            double lastX = this.getX(size - 1);
            double lastY = this.getY(size - 1);
            if (firstX != lastX || firstY != lastY) {
                this.points.add(firstX, firstY);
            }
        }
    }

    public boolean isEmpty() {
        return this.points.isEmpty();
    }

    private static class ScalingSequence
    extends MutableCoordinateSequence {
        private final double scale;
        private final double relX;
        private final double relY;

        public ScalingSequence(double scale, double relX, double relY) {
            this.scale = scale;
            this.relX = relX;
            this.relY = relY;
        }

        @Override
        public void addPoint(double x, double y) {
            super.addPoint(this.scale * (x - this.relX), this.scale * (y - this.relY));
        }
    }
}

