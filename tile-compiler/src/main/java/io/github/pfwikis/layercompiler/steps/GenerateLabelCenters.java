package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    		"-filter", "Boolean(label)",
    		dissolve?List.of("-dissolve2", "label", "sum-fields=areaSqkm", "copy-fields=inSubregion,color"):List.of(),
    		"-sort", "areaSqkm", "descending", //to make bigger feature more important
    		"-each", "tmpMercatorScaling="+mercatorScaling("this.centroidY"),
    		"-each", "tmpAdjustedSize=Math.sqrt(areaSqkm)*tmpMercatorScaling",
			"-each", "filterMinzoom="+filterMinzoom(),
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
    		cl.setLabel(f.getProperties().getLabel());
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
    		props.setLabel(newF.getProperties().getLabel());
    		props.setAngle(angles.get(props.getUuid()));
    		var p = new Point();
    		p.setCoordinates(innerPoints.get(props.getUuid()));
    		newF.setGeometry(p);
    		res.getFeatures().add(newF);
    	}
    	return res;
	}

	private Map<UUID, BigDecimal> calcAngles(LCContent withUuid) throws IOException {
    	var orientedRectangles = Tools.qgis(this, "qgis:minimumboundinggeometry", withUuid,
    		"--TYPE=1",
    		"--FIELD=uuid"
		).toFeatureCollectionAndFinish();

        var angles = orientedRectangles
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
        return angles;
	}
	
	private Map<UUID, LngLat> calcInnerPoint(LCContent withUuid) throws IOException {
		//sometimes buffering fails so we have to fallback on normal inner points
		var buffered = Tools.qgis(this, "native:buffer", withUuid, "--DISTANCE=field:bufferSize");
        var inner = Tools.mapshaper(this, buffered, "-dissolve2", "uuid", "-points", "inner").toFeatureCollectionAndFinish();
        buffered.finishUsage();
        var best = inner
    		.getFeatures()
    		.stream()
    		.filter(f->f.getGeometry()!=null)
    		.collect(Collectors.<Feature,UUID,LngLat>toMap(
				f->f.getProperties().getUuid(),
				f->((Point)f.getGeometry()).getCoordinates()
			));
        
        var fallback = Tools.mapshaper(this, withUuid, "-points", "inner").toFeatureCollectionAndFinish()
    		.getFeatures()
    		.stream()
    		.collect(Collectors.<Feature,UUID,LngLat>toMap(
				f->f.getProperties().getUuid(),
				f->((Point)f.getGeometry()).getCoordinates()
			));
        
        var result = new HashMap<>(fallback);
        result.putAll(best);
        return result;
	}

	private boolean addLowerZoomLabels(FeatureCollection result, LCContent polygons, List<Field<?>> fieldsToCopy, int step) throws IOException {
    	int extraZoom = (labelRange+1)*step;
    	var features = Tools.mapshaper(
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
				"copy-fields=dots,label,filterMinzoom,filterMaxzoom,areaSqkm"+(fieldsToCopy.isEmpty()?"":(","+fieldsToCopy.stream().map(Field::name).collect(Collectors.joining(",")))),
				"multipart"
		).toFeatureCollectionAndFinish();
    	log.info("Added {} label points at zoom shift {}", features.getFeatures().size(), step);
    	result.getFeatures().addAll(features.getFeatures());
    	return !features.getFeatures().isEmpty();
	}
	

	private String filterMinzoom() {
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
