package io.github.pfwikis.fractaldetailer.model;

public class Project {
    private static final int TILE_SIZE = 10000;
    private static Point _pixelOrigin;
    private static double _pixelsPerLonDegree;
    private static double _pixelsPerLonRadian;

    static {
        _pixelOrigin = new Point(TILE_SIZE / 2.0, TILE_SIZE / 2.0);
        _pixelsPerLonDegree = TILE_SIZE / 360.0;
        _pixelsPerLonRadian = TILE_SIZE / (2 * Math.PI);
    }

    static double bound(double val, double valMin, double valMax) {
        double res;
        res = Math.max(val, valMin);
        res = Math.min(res, valMax);
        return res;
    }

    static double degreesToRadians(double deg) {
        return deg * (Math.PI / 180);
    }

    static double radiansToDegrees(double rad) {
        return rad / (Math.PI / 180);
    }

    public static Point fromLatLngToPoint(LngLat lngLat) {
        double x = _pixelOrigin.x() + lngLat.lng() * _pixelsPerLonDegree;

        // Truncating to 0.9999 effectively limits latitude to 89.189. This is
        // about a third of a tile past the edge of the world tile.
        double siny = bound(Math.sin(degreesToRadians(lngLat.lat())), -0.9999, 0.9999);
        double y = _pixelOrigin.y() + 0.5 * Math.log((1 + siny) / (1 - siny)) * -_pixelsPerLonRadian;

        return new Point(x, y);
    }

    public static LngLat fromPointToLatLng(Point point) {
        double lng = (point.x() - _pixelOrigin.x()) / _pixelsPerLonDegree;
        double latRadians = (point.y() - _pixelOrigin.y()) / -_pixelsPerLonRadian;
        double lat = radiansToDegrees(2 * Math.atan(Math.exp(latRadians)) - Math.PI / 2);
        return new LngLat(lng, lat);
    }
}
