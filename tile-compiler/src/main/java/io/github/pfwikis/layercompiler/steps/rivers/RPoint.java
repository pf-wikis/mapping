package io.github.pfwikis.layercompiler.steps.rivers;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.math.Vector2D;

import io.github.pfwikis.util.Projection;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import mil.nga.sf.geojson.Point;
import mil.nga.sf.geojson.Position;

@Getter @Setter
@RequiredArgsConstructor
@EqualsAndHashCode(of = "location")
public class RPoint implements Comparable<RPoint> {

    private final Vector2D location;
    private double width = -1;
    private boolean segmentEnd = false;
    private boolean spring = false;
    private boolean interpolated = false;
    private final List<RPoint> neighbors = new ArrayList<>();

    @Override
    public int compareTo(RPoint o) {
        return Double.compare(location.getY()*400d+location.getX(), o.location.getY()*400d+o.location.getX());
    }

    public double distanceTo(RPoint o) {
        var y1 = Projection.mercatorToGeo(location.getY());
        var y2 = Projection.mercatorToGeo(o.location.getY());

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(y2 - y1);
        double lonDistance = Math.toRadians(o.location.getX() - location.getX());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(y1)) * Math.cos(Math.toRadians(y2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // convert to meters
    }

    /*public static Vector2D v(mil.nga.sf.Point p) {
        return new Vector2D(p.getX()*2/3, p.getY());
    }

    public static Point p(Vector2D v) {
        return new Point(new Position(v.getX()*3/2, v.getY()));
    }*/

    public static Vector2D v(mil.nga.sf.Point p) {
        return new Vector2D(p.getX(), Projection.geoToMercator(p.getY()));
    }

    public static Point p(Vector2D v) {
        return new Point(new Position(v.getX(), Projection.mercatorToGeo(v.getY())));
    }

    @Override
    public String toString() {
        return "[lat="+p(location).getPosition().getY()+", lon="+p(location).getPosition().getX()+"]";
    }
}
