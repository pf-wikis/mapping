package io.github.pfwikis.layercompiler.steps;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.Geometry.LineString;
import io.github.pfwikis.model.Geometry.MultiLineString;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.CatmullRomSpline;
import io.github.pfwikis.util.Projection;

public class SmoothLines extends LCStep {

    @Override
    public LCContent process() throws IOException {
    	var fc = getInput().toFeatureCollection();
    	for(var f:fc.getFeatures()) {
    		if(f.getGeometry() instanceof LineString line) {
    			f.setGeometry(new LineString(interpolate(line.getCoordinates())));
    		}
    		else if(f.getGeometry() instanceof MultiLineString lines) {
    			lines.getCoordinates().replaceAll(SmoothLines::interpolate);
    		}
    	}
    	
    	//this explosion also guarantees that any in-memory work uses exactly the same
    	//data as any mapshaper work
    	return Tools.mapshaper(this, LCContent.from(fc), "-explode");
    }

    private static List<LngLat> interpolate(List<LngLat> coordinates) {
    	List<Point2D> points = new ArrayList<Point2D>(coordinates.stream().map(p->new Point2D.Double(p.lng(), Projection.geoToMercator(p.lat()))).toList());
    	
    	while(true) {
    		boolean changed = false;
    		var mask = new boolean[points.size()];
    		for(int i=0;i<points.size()-2;i++) {
    			//area of triangle
    			double angle = angle(points.get(i), points.get(i+1), points.get(i+2));
    			// angle>0.1 is a sanity check since the spline runs rampant on extreme angles
    			if(angle < 175 && angle > 0.1) {
    				changed = true;
    				mask[i] = true;
    				mask[i+1] = true;
    			}
    		}
    		if(!changed)
    			break;
    		var spline = CatmullRomSpline.create(points, 2,	0.5).getInterpolatedPoints();
    		var newPoints = new ArrayList<Point2D>();
    		for(int i=0;i<points.size();i++) {
    			newPoints.add(points.get(i));
    			if(mask[i]) {
    				newPoints.add(spline.get(2*i+1));
    			}
    		}
    		points=newPoints;
    	}
		var result = new ArrayList<>(points.stream()
			.map(p->new LngLat(p.getX(), Projection.mercatorToGeo(p.getY())))
			.toList());
		
		//because of the 3 point angel problem we could still try to interpolate
		//on an angle of 0°
		//since we keep the control points we can savely remove errored points
		result.removeIf(p->Double.isNaN(p.lat()) || Double.isNaN(p.lng()));
		
		return result;
	}

	private static double angle(Point2D p1, Point2D p2, Point2D p3) {
		double angle = Math.abs(Math.toDegrees(
			Math.atan2(p3.getY() - p2.getY(), p3.getX() - p2.getX()) -
			Math.atan2(p1.getY() - p2.getY(), p1.getX() - p2.getX())));
		if(angle > 180) angle=360-angle;
		return angle;
	}
}
