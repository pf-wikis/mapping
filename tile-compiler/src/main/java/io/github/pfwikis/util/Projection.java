package io.github.pfwikis.util;

import org.locationtech.proj4j.ProjCoordinate;
import org.locationtech.proj4j.proj.MercatorProjection;

public class Projection {

    private final static MercatorProjection PROJ = new MercatorProjection();
    private final static ProjCoordinate PROJ_OUT = new ProjCoordinate();
    private final static double SPECIAL_NUMBER = toMercator(85);

    public synchronized static double geoToMercator(double lat) {
        if(lat>85)
            return toMercator(lat-85)+SPECIAL_NUMBER;
        else if(lat<-85)
            return toMercator(lat+85)-SPECIAL_NUMBER;
        else
            return toMercator(lat);
    }
    private synchronized static double toMercator(double lat) {
        PROJ.project(0, Math.toRadians(lat), PROJ_OUT);
        return Math.toDegrees(PROJ_OUT.y);
    }

    public synchronized static double mercatorToGeo(double y) {
        if(y>SPECIAL_NUMBER)
            return toGeo(y-SPECIAL_NUMBER)+85;
        else if(y<-SPECIAL_NUMBER)
            return toGeo(y+SPECIAL_NUMBER)-85;
        else
            return toGeo(y);
    }
    private synchronized static double toGeo(double y) {
        PROJ.projectInverse(0, Math.toRadians(y), PROJ_OUT);
        return Math.toDegrees(PROJ_OUT.y);
    }
}
