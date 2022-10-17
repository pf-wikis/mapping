package io.github.pfwikis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.geojson.LngLatAlt;

public class FractalLines {

    private final static double MAX_DIST = 1000;
	private final static double FRACTAL_STRENGTH = 0.3;
	private final static double FRACTAL_SCALE = 0.001;

	public static List<LngLatAlt> interpolate(List<LngLatAlt> points, boolean closed) {
		ArrayDeque<LngLatAlt> open = new ArrayDeque<>(points);
		//for interpolation between first and last point
		if(closed)
			open.add(open.getFirst());
		List<LngLatAlt> result = new ArrayList<>();
		result.add(open.pop());
		LngLatAlt a = result.get(0);

		while(!open.isEmpty()) {
			LngLatAlt b = open.pop();

			double distance = distance(a,b);
			if(distance > MAX_DIST) {
				FastNoiseLite noise = new FastNoiseLite();
				noise.SetSeed(Long.hashCode(generateRandomSeed(a, b)));
				collectFractally(distance,a,b,result,0,1,noise);
			}
			a = b;
			result.add(b);
		}
		//if closed remove the last point again
		if(closed)
			result.remove(result.size()-1);
		return result;
	}

	public static long generateRandomSeed(LngLatAlt a, LngLatAlt b) {
        return Double.doubleToLongBits(a.getLongitude())
            + Double.doubleToLongBits(a.getLatitude())
            + Double.doubleToLongBits(b.getLongitude())
            + Double.doubleToLongBits(b.getLongitude());
    }

	private static void collectFractally(double initialDistance, LngLatAlt a, LngLatAlt b, List<LngLatAlt> result, double posA, double posB, FastNoiseLite noise) {
		double distance = distance(a,b);
		if(distance > MAX_DIST) {
			double posM = (posB-posA)/2d;
			double r = noise.GetNoise((float)(posM * initialDistance * FRACTAL_SCALE), 0); //fishy

			double d = FRACTAL_STRENGTH*r;
			double aY = a.getLatitude();
			double aX = a.getLongitude();
			double bY = b.getLatitude();
			double bX = b.getLongitude();

			double ma = 0.5;//r.nextDouble()*0.5+0.25;
			double mb = 0.5;//1-ma;

			LngLatAlt m = new LngLatAlt(
				ma*aX+mb*bX + (aY-bY)*d,
				ma*aY+mb*bY - (aX-bX)*d
			);

			collectFractally(initialDistance, a, m, result, posA, posM, noise);
			result.add(m);
			collectFractally(initialDistance, m, b, result, posM, posB, noise);
		}
	}

	public static double distance(LngLatAlt p1, LngLatAlt p2) {

        final int R = 6371; // Radius of the earth

        double latDistance = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double lonDistance = Math.toRadians(p2.getLongitude() - p1.getLongitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(p1.getLatitude())) * Math.cos(Math.toRadians(p2.getLatitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c * 1000; // convert to meters
    }
}
