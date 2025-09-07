package io.github.pfwikis.util;

import org.locationtech.proj4j.ProjCoordinate;
import org.locationtech.proj4j.proj.MercatorProjection;

public class Projection {

    private final static MercatorProjection PROJ = new MercatorProjection();
    private final static double SPECIAL_CUTOFF = 85;
    private final static double SPECIAL_NUMBER = toMercator(SPECIAL_CUTOFF);
    private final static double Y_FACTOR = 180d/(SPECIAL_NUMBER+5);

    public static double geoToMercator(double lat) {
    	lat = Math.clamp(lat, -90d, 90d);
        if(lat>SPECIAL_CUTOFF)
            return  Y_FACTOR*(lat-SPECIAL_CUTOFF+SPECIAL_NUMBER);
        else if(lat<-SPECIAL_CUTOFF)
            return  Y_FACTOR*(lat+SPECIAL_CUTOFF-SPECIAL_NUMBER);
        else
            return  Y_FACTOR*toMercator(lat);
    }
    private static double toMercator(double lat) {
    	var out = new ProjCoordinate();
        PROJ.project(0, Math.toRadians(lat), out);
        return Math.toDegrees(out.y);
    }

    public static double mercatorToGeo(double y) {
    	y=Math.clamp(y, -180d, 180d)/Y_FACTOR;
        if(y>SPECIAL_NUMBER)
            return y-SPECIAL_NUMBER+SPECIAL_CUTOFF;
        else if(y<-SPECIAL_NUMBER)
            return y+SPECIAL_NUMBER-SPECIAL_CUTOFF;
        else
            return toGeo(y);
    }
    private static double toGeo(double y) {
    	var out = new ProjCoordinate();
        PROJ.projectInverse(0, Math.toRadians(y), out);
        return Math.toDegrees(out.y);
    }
}
