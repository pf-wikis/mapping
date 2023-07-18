package io.github.pfwikis.util;

import org.locationtech.proj4j.ProjCoordinate;
import org.locationtech.proj4j.proj.MercatorProjection;

public class Projection {

    private final static MercatorProjection PROJ = new MercatorProjection();
    private final static ProjCoordinate PROJ_OUT = new ProjCoordinate();

    public synchronized static double geoToMercator(double lat) {
        PROJ.project(0, Math.toRadians(lat), PROJ_OUT);
        return Math.toDegrees(PROJ_OUT.y);
    }

    public synchronized static double mercatorToGeo(double y) {
        PROJ.projectInverse(0, Math.toRadians(y), PROJ_OUT);
        return Math.toDegrees(PROJ_OUT.y);
    }
}
