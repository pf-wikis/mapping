package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry.Point;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.run.Tools;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
public class GenerateLabelCenters extends LCStep {
	
	private static record Field<T>(String name, Function<Properties, T> getter, BiConsumer<Properties, T> setter) {
		void copy(Properties from, Properties to) {
			setter.accept(to, getter.apply(from));
		}
	}
	private static final List<Field<?>> FIELDS_TO_COPY = List.of(
			new Field<>("color", Properties::getColor, Properties::setColor),
			new Field<>("type", Properties::getType, Properties::setType),
			new Field<>("inSubregion", Properties::getInSubregion, Properties::setInSubregion)
	);
	
	private int labelRange = 3;

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
    		"-filter", "Boolean(Name)",
    		dissolve?List.of("-dissolve2", "Name", "sum-fields=areaSqkm", "copy-fields=inSubregion,color"):List.of(),
    		"-each", "tmpMercatorScaling="+mercatorScaling("this.centroidY"),
    		"-each", "tmpAdjustedSize=Math.sqrt(areaSqkm)*tmpMercatorScaling",
			"-each", "filterMinzoom="+filterMinzoom(this.getName()),
            "-each", "filterMaxzoom=filterMinzoom+"+labelRange,
            "-each", "bufferSize=Math.sqrt(areaSqkm)*0.0002"
		);
        withArea.finishUsage();
        
        var withUuid = withZoomRes.toFeatureCollection();
        withZoomRes.finishUsage();
        withUuid.getFeatures().forEach(f->f.getProperties().setUuid(UUID.randomUUID()));
        
        var fieldsToCopy = new ArrayList<>(FIELDS_TO_COPY);
        fieldsToCopy.removeIf(f->withUuid.getFeatures().stream().noneMatch(fet->f.getter.apply(fet.getProperties())!=null));

        var withUuidRes = LCContent.from(withUuid);
        
        var innerPoints = calcInnerPoint(withUuidRes);
        var angles = calcAngles(withUuidRes);

        var withInfo = mergeInfos(withUuid, innerPoints, angles);
        
        if(!generateSubLabels) {
        	return LCContent.from(cleanProperties(withInfo, fieldsToCopy));
        }

        FeatureCollection merged = withInfo.copy();
        for(int i=1;true;i++) {
        	var changed=addLowerZoomLabels(merged, withUuidRes, fieldsToCopy, i);
        	if(!changed) {
        		break;
        	}
        } 

        return LCContent.from(cleanProperties(merged, fieldsToCopy));
    }

    private FeatureCollection cleanProperties(FeatureCollection fc, List<Field<?>> fieldsToCopy) {
    	for(var f:fc.getFeatures()) {
    		var cl = new Properties();
    		cl.setName(f.getProperties().getName());
    		cl.setColor(f.getProperties().getColor());
    		cl.setAngle(f.getProperties().getAngle());
    		cl.setFilterMaxzoom(f.getProperties().getFilterMaxzoom());
    		cl.setFilterMinzoom(f.getProperties().getFilterMinzoom());
    		for(var field:fieldsToCopy) {
    			field.copy(f.getProperties(), cl);
    		}
    		f.setProperties(cl);
    	}
		return fc;
	}

	private FeatureCollection mergeInfos(
			FeatureCollection withUuid,
			Map<UUID, LngLat> innerPoints,
			Map<UUID, BigDecimal> angles) {

    	var res = new FeatureCollection();
    	for(var f : withUuid.getFeatures()) {
			Properties props = f.getProperties().copy();
			if(!innerPoints.containsKey(props.getUuid())) throw new IllegalStateException(f+" has no inner point");
			var newF = new Feature();
    		newF.setProperties(props);
    		props.setName(newF.getProperties().getName());
    		props.setAngle(angles.get(props.getUuid()));
    		var p = new Point();
    		p.setCoordinates(innerPoints.get(props.getUuid()));
    		newF.setGeometry(p);
    		res.getFeatures().add(newF);
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
    				if(angle < 10 && angle > -10)
    					return BigDecimal.ZERO;
    				else
    					return BigDecimal.valueOf(angle);
    			}
			));
        orientedRectanglesRes.finishUsage();
        return angles;
	}
	
	private Map<UUID, LngLat> calcInnerPoint(LCContent withUuid) throws IOException {
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

	
	//no longer used, kept bcs they might be useful in the future
	/*
	//returns centroids only if they are within their parent polygon
	private Map<UUID, LngLat> calcContainedCentroids(LCContent withUuid) throws IOException {
        var centroidsRes = Tools.qgis(this, "native:centroids", withUuid, "--ALL_PARTS=false");
        var containedOnly = Tools.qgis(this, "qgis:clip", centroidsRes,
    		new Runner.TmpGeojson("--OVERLAY=", withUuid));
        //centroidsRes.finishUsage();
        return containedOnly.toFeatureCollection()
    		.getFeatures()
    		.stream()
    		.collect(Collectors.<Feature,UUID,LngLat>toMap(
				f->f.getProperties().getUuid(),
				f->{
					if(f.getGeometry() instanceof Point p)
						return p.getCoordinates();
					if(f.getGeometry() instanceof MultiPoint p && p.getCoordinates().size()==1)
						return p.getCoordinates().get(0);
					throw new ClassCastException(f.getGeometry().getClass()+" can't be cast to Point");
				}
			));
	}
	
	
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
*/

	private boolean addLowerZoomLabels(FeatureCollection result, LCContent polygons, List<Field<?>> fieldsToCopy, int step) throws IOException {
    	int extraZoom = (labelRange+1)*step;
    	var reduced = Tools.mapshaper(
			this, 
			polygons,			
			"-filter", "filterMinzoom+"+extraZoom+"<="+(ctx.getOptions().getMaxZoom()+labelRange),
			"-each", "dots=4**("+step+")",
			"-each", "filterMinzoom=filterMinzoom+"+extraZoom,
			"-each", "filterMaxzoom=filterMaxzoom+"+extraZoom,
			"-each", "name=name+' +"+step+"'",
			"-dots",
				"dots",
				"evenness=1",
				"copy-fields=dots,Name,filterMinzoom,filterMaxzoom,areaSqkm"+(fieldsToCopy.isEmpty()?"":(","+fieldsToCopy.stream().map(Field::name).collect(Collectors.joining(",")))),
				"multipart"
		);
    	var features = reduced.toFeatureCollection();
    	log.info("Added {} label points at zoom shift {}", features.getFeatures().size(), step);
    	result.getFeatures().addAll(features.getFeatures());
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
