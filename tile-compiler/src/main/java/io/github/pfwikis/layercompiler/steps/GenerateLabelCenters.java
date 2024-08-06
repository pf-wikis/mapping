package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.Point;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.Tools;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
public class GenerateLabelCenters extends LCStep {
	
	private static final int LABEL_RANGE = 3;

	private boolean dissolve = true;
	private boolean generateSubLabels = true;
	private Integer forceMinzoom = null;
	
    @Override
    public LCContent process() throws IOException {
    	LCContent in = getInput();

        log.info("  Generating label points from polygon centers");
        //mapshaper area is sometimes inconsistent so we use qgis
        var withArea = Tools.qgis(this, "native:fieldcalculator", in,
    		"--FIELD_NAME=areaSqkm",
    		"--FIELD_TYPE=0", //float
    		"--FIELD_PRECISION=7",
    		"--FORMULA=$area/1000000"
		);

        var withZoomRes = Tools.mapshaper(this, withArea,
    		"-filter", "Name != null",
    		dissolve?List.of("-dissolve2", "Name", "sum-fields=areaSqkm", "copy-fields=inSubregion,areaSqkm"):List.of(),
    		"-each", "tmpMercatorScaling="+mercatorScaling("this.centroidY"),
    		"-each", "tmpAdjustedSize=Math.sqrt(areaSqkm)*tmpMercatorScaling",
			"-each", "filterMinzoom="+filterMinzoom(this.getName()),
            "-each", "filterMaxzoom=filterMinzoom+"+LABEL_RANGE,
            "-each", "bufferSize=Math.sqrt(areaSqkm)*0.0002"
		);
        withArea.finishUsage();
        
        var withUuid = withZoomRes.toFeatureCollection();
        withZoomRes.finishUsage();
        withUuid.getFeatures().forEach(f->f.getProperties().setUuid(UUID.randomUUID()));
        var withUuidRes = LCContent.from(withUuid);
        
        var polesOfInaccessibility = calcPoleOfInaccessibility(withUuidRes);
        //var centroids = calcContainedCentroids(withUuidRes);
        var innerPoints = calcInnerPoints(withUuidRes);
        var angles = calcAngles(withUuidRes);

        var result = mergeInfos(withUuid, polesOfInaccessibility, Map.of()/*centroids*/, innerPoints, angles);
        
        if(!generateSubLabels) {
        	return LCContent.from(result);
        }

        /*
        for(int i=0;true;i++) {
        	var changed=addLowerZoomLabels(result, withZoom, i+1);
        	if(!changed) {
        		break;
        	}
        } 
        */
        return LCContent.from(result);
    }

    private FeatureCollection mergeInfos(
			FeatureCollection withUuid,
			Map<UUID, LngLat> polesOfInaccessibility,
			Map<UUID, LngLat> centroids,
			Map<UUID, LngLat> innerPoints,
			Map<UUID, BigDecimal> angles) {

    	var res = new FeatureCollection();
    	var maps = List.of(polesOfInaccessibility, centroids, innerPoints);
    	for(var f : withUuid.getFeatures()) {
    		for(var map:maps) {
    			var props = f.getProperties();
    			if(!map.containsKey(props.getUuid())) continue;
    			var newF = new Feature();
        		newF.setProperties(props);
        		props.setAngle(angles.get(props.getUuid()));
        		var p = new Point();
        		p.setCoordinates(map.get(props.getUuid()));
        		newF.setGeometry(p);
        		res.getFeatures().add(newF);
    		}
    		/*
    		var newF = new Feature();
    		var props = f.getProperties();
    		newF.setProperties(props);
    		props.setAngle(angles.get(props.getUuid()));
    		var p = new Point();
    		p.setCoordinates(polesOfInaccessibility.get(props.getUuid()).middle(centroids.get(props.getUuid())));
    		newF.setGeometry(p);
    		res.getFeatures().add(newF);*/
    	}
    	return res;
	}

	private Map<UUID, BigDecimal> calcAngles(LCContent withUuid) throws IOException {
    	var orientedRectanglesRes = Tools.qgis(this, "qgis:minimumboundinggeometry", withUuid,
    		"--TYPE=1",
    		"--FIELD=uuid"
		);

        var angles = orientedRectanglesRes.toFeatureCollection()
    		.getFeatures().stream()
    		.collect(Collectors.<Feature, UUID, BigDecimal>toMap(
    			f->f.getProperties().getUuid(),
    			f-> {
    				var width = f.getProperties().getWidth().doubleValue();
    				var height = f.getProperties().getHeight().doubleValue();
    				//do not rotate if the actual rectangle is more of a square
    				if(height/width<1.75d)
    					return BigDecimal.ZERO;
    				
    				var angle = f.getProperties().getAngle()
						.subtract(BigDecimal.valueOf(90))
						.setScale(0, RoundingMode.HALF_UP)
						.intValueExact();
    				if(angle < 15 && angle > -15)
    					return BigDecimal.ZERO;
    				else
    					return BigDecimal.valueOf(angle);
    			}
			));
        orientedRectanglesRes.finishUsage();
        return angles;
	}
	
	private Map<UUID, LngLat> calcInnerPoints(LCContent withUuid) throws IOException {
        var innerRes = Tools.mapshaper(this, withUuid, "-points", "inner");
        var inner = innerRes.toFeatureCollection();
        innerRes.finishUsage();
        return inner
    		.getFeatures()
    		.stream()
    		.collect(Collectors.<Feature,UUID,LngLat>toMap(
				f->f.getProperties().getUuid(),
				f->((Point)f.getGeometry()).getCoordinates()
			));
	}

	/*returns centroids only if they are within their parent polygon*/
	/*private Map<UUID, LngLat> calcContainedCentroids(LCContent withUuid) throws IOException {
        var centroidsRes = Tools.qgis(this, "native:centroids", withUuid);
        var containedOnly = Tools.qgis(this, "qgis:intersection", centroidsRes,
    		new Runner.TmpGeojson("--OVERLAY=", withUuid)
    		"--OVERLAY=")
        centroidsRes.finishUsage();
        return centroids
    		.getFeatures()
    		.stream()
    		.collect(Collectors.<Feature,UUID,LngLat>toMap(
				f->f.getProperties().getUuid(),
				f->((Point)f.getGeometry()).getCoordinates()
			));
	}*/

	private Map<UUID, LngLat> calcPoleOfInaccessibility(LCContent withUuid) throws IOException {
    	//buffer first to fill gaps in highly distributed forests
        var buffered = Tools.qgis(this, "native:buffer", withUuid,
            "--DISTANCE=field:bufferSize",
            "--END_CAP_STYLE=0", "--JOIN_STYLE=0", "--MITER_LIMIT=2",
            "--DISSOLVE=field:Name");
        
        var mainPointsRes = Tools.qgis(this, "native:poleofinaccessibility", buffered,
    		"--TOLERANCE=1e-06"
        );
        buffered.finishUsage();
        var mainPoints = mainPointsRes.toFeatureCollection();
        mainPointsRes.finishUsage();
        return mainPoints
    		.getFeatures()
    		.stream()
    		.collect(Collectors.<Feature,UUID,LngLat>toMap(
				f->f.getProperties().getUuid(),
				f->((Point)f.getGeometry()).getCoordinates()
			));
	}

	private boolean addLowerZoomLabels(FeatureCollection result, LCContent withZoom, int step) throws IOException {
    	int extraZoom = (LABEL_RANGE+1)*step;
    	var reduced = Tools.mapshaper(
			this, 
			withZoom,			
			"-filter", "filterMinzoom+"+extraZoom+"<="+(ctx.getOptions().getMaxZoom()+LABEL_RANGE),
			"-each", "dots=2**("+(extraZoom/2)+")",
			"-dots",
    			"dots",
    			"copy-fields=dots,Name,filterMinzoom,filterMaxzoom,areaSqkm",
    			"multipart",
			"-each", "filterMinzoom=filterMinzoom+"+extraZoom,
			"-each", "filterMaxzoom=filterMaxzoom+"+extraZoom
		);
    	var features = reduced.toFeatureCollection();
    	add(result, features, Map.of());
    	reduced.finishUsage();
    	return !features.getFeatures().isEmpty();
	}

	private String filterMinzoom(String name) {
		if(forceMinzoom != null)
			return Integer.toString(forceMinzoom);

        return "Math.floor(Math.log2(10000/(Math.sqrt(areaSqkm*%s))))"
        	.formatted(mercatorScaling("this.centroidY"));
    }

	private String mercatorScaling(String lat) {
		return "1/Math.cos(%s/180*Math.PI)"
			.formatted(lat);
	}
}
