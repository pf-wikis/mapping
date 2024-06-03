package io.github.pfwikis.util;

import org.locationtech.proj4j.ProjCoordinate;
import org.locationtech.proj4j.proj.MercatorProjection;

public class Projection {

    private final static MercatorProjection PROJ = new MercatorProjection();
    private final static ProjCoordinate PROJ_OUT = new ProjCoordinate();
    private final static double SPECIAL_CUTOFF = 85;
    public final static double SPECIAL_NUMBER = toMercator(SPECIAL_CUTOFF);

    public synchronized static double geoToMercator(double lat) {
        if(lat>SPECIAL_CUTOFF)
            return toMercator(lat-SPECIAL_CUTOFF)+SPECIAL_NUMBER;
        else if(lat<-SPECIAL_CUTOFF)
            return toMercator(lat+SPECIAL_CUTOFF)-SPECIAL_NUMBER;
        else
            return toMercator(lat);
    }
    private synchronized static double toMercator(double lat) {
        PROJ.project(0, Math.toRadians(lat), PROJ_OUT);
        return Math.toDegrees(PROJ_OUT.y);
    }

    public synchronized static double mercatorToGeo(double y) {
        if(y>SPECIAL_NUMBER)
            return toGeo(y-SPECIAL_NUMBER)+SPECIAL_CUTOFF;
        else if(y<-SPECIAL_NUMBER)
            return toGeo(y+SPECIAL_NUMBER)-SPECIAL_CUTOFF;
        else
            return toGeo(y);
    }
    private synchronized static double toGeo(double y) {
        PROJ.projectInverse(0, Math.toRadians(y), PROJ_OUT);
        return Math.toDegrees(PROJ_OUT.y);
    }
}
