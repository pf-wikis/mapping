package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.hull.ConcaveHullOfPolygons;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Geometry;
import io.github.pfwikis.model.Geometry.Point;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.Jackson;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class GenerateLabelCenters extends StepExecutor {
	
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
	public Content process(Inputs in) throws Exception {
    	if(in.getInput().isEmpty()) return Content.empty();
    	log.info("  Generating label points from polygon centers");

        var polygonsRes = prepareGeometry(in);
        var polygons = polygonsRes.toFeatureCollection();
        
        var fieldsToCopy = new ArrayList<>(FIELDS_TO_COPY);
        fieldsToCopy.removeIf(f->polygons.getFeatures().stream().noneMatch(fet->f.getter.apply(fet.getProperties())!=null));

        
        var innerPoints = calcInnerPoint(polygonsRes);
        var angles = calcAngles(polygonsRes);

        var withInfo = mergeInfos(polygons, innerPoints, angles);
        
        if(!generateSubLabels) {
        	return Content.timeless(GeoData.from(cleanProperties(withInfo, fieldsToCopy)));
        }

        FeatureCollection merged = withInfo.copy();
        for(int i=1;true;i++) {
        	var changed=addLowerZoomLabels(merged, polygonsRes, fieldsToCopy, i);
        	if(!changed) {
        		break;
        	}
        }
        
        return Content.timeless(GeoData.from(cleanProperties(merged, fieldsToCopy)));
	}

    private GeoData prepareGeometry(Inputs in) throws IOException {
    	try(var _=this.measureSubtime("prepareGeometry")) {
	    	var dissolved = Tools.mapshaper(this, in.getInput(),
	    		"-filter", "Boolean(label)",
	    		dissolve?List.of("-dissolve", "label"+in.getTimeState().mapshaperTimeFields(), "allow-overlaps", "copy-fields=inSubregion,color"):List.of()
	    	);
	        
	    	//mapshaper sometimes gives weird results here
	        var withArea = Tools.qgis(this, "native:fieldcalculator", dissolved,
	    		"--FIELD_NAME=areaSqkm",
	    		"--FIELD_TYPE=0", //float
	    		"--FIELD_PRECISION=7",
	    		"--FORMULA=try($area/1000000,0)"
			);
	        
	        var withFields = Tools.mapshaper(this, withArea,
	    		dissolve?List.of("-dissolve", "label"+in.getTimeState().mapshaperTimeFields(), "allow-overlaps", "copy-fields=inSubregion,color", "sum-fields=areaSqkm"):List.of(),
	    		"-sort", "areaSqkm", "descending", //to make bigger feature more important
				"-each", "minzoom="+minzoomJS(),
	            "-each", "maxzoom=minzoom+"+labelRange,
	            "-require", "crypto",
	            "-each", "uuid=crypto.randomUUID()"
	    	);
	        return withFields;
    	}
	}

	private FeatureCollection cleanProperties(FeatureCollection fc, List<Field<?>> fieldsToCopy) {
    	for(var f:fc.getFeatures()) {
    		var cl = new Properties();
    		cl.setLabel(f.getProperties().getLabel());
    		cl.setColor(f.getProperties().getColor());
    		cl.setAngle(f.getProperties().getAngle());
    		cl.setMaxzoom(f.getProperties().getMaxzoom());
    		cl.setMinzoom(f.getProperties().getMinzoom());
    		cl.setTime(f.getProperties().getTime());
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

		try(var _=this.measureSubtime("mergeInfos")) {
	    	var res = new FeatureCollection();
	    	for(var f : withUuid.getFeatures()) {
				Properties props = f.getProperties().copy();
				if(!innerPoints.containsKey(props.getUuid()))
					throw new IllegalStateException(f+" has no inner point");
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
	}

	private Map<UUID, BigDecimal> calcAngles(GeoData withUuid) throws IOException {
		try(var _=this.measureSubtime("calcAngles")) {
	    	var orientedRectangles = Tools.qgis(this, "qgis:minimumboundinggeometry", withUuid,
	    		"--TYPE=1",
	    		"--FIELD=uuid"
			).toFeatureCollection();
	
	        var angles = orientedRectangles
	    		.getFeatures().stream()
	    		.collect(Collectors.<Feature, UUID, BigDecimal>toMap(
	    			f->f.getProperties().getUuid(),
	    			f-> {
	    				var width = f.getProperties().getWidth().doubleValue();
	    				var height = f.getProperties().getHeight().doubleValue();
	    				//do not rotate if the actual rectangle is more of a square
	    				if(height/width<1.5d)
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
	}
	
	private static final GeoJsonWriter GEO_WRITER;
	
	static {
		GEO_WRITER = new GeoJsonWriter();
		GEO_WRITER.setEncodeCRS(false);
	}
	
	private Map<UUID, LngLat> calcInnerPoint(GeoData in) throws IOException {
		try(var _=this.measureSubtime("calcInnerPoint")) {
			var fc = in.toFeatureCollection();
	        var hulled = new FeatureCollection();
	        fc.getFeatures()
	        	.stream()
	        	.map(f -> {
	        		try {
		        		var json = Jackson.JSON.writeValueAsString(f);
		        		
		        		var hull = ConcaveHullOfPolygons.concaveHullByLengthRatio(new GeoJsonReader().read(json), 0.15);
		        		var geom = Jackson.JSON.readValue(GEO_WRITER.write(hull), Geometry.class);
		        		var res = new Feature();
		        		res.setProperties(f.getProperties());
		        		res.setGeometry(geom);
		        		return res;
	        		} catch(Exception e) {
	        			log.warn("Can't generate concave hull for {}:{}", description.getId(), f, e.getMessage());
	        			return f;
	        		}
	        	})
	        	.forEach(hulled.getFeatures()::add);
	        var buffered = GeoData.from(hulled);
			
	        var inner = Tools.mapshaper(this, buffered, "-points", "inner").toFeatureCollection();
	        return inner
	    		.getFeatures()
	    		.stream()
	    		.filter(f->f.getGeometry()!=null)
	    		.collect(Collectors.<Feature,UUID,LngLat>toMap(
					f->f.getProperties().getUuid(),
					f->((Point)f.getGeometry()).getCoordinates()
				));
		}
	}

	private boolean addLowerZoomLabels(FeatureCollection result, GeoData polygons, List<Field<?>> fieldsToCopy, int step) throws IOException {
		try(var _=this.measureSubtime("addLowerZoomLabels")) {
	    	int extraZoom = (labelRange+1)*step;
	    	var features = Tools.mapshaper(
				this, 
				polygons,			
				"-filter", "minzoom+"+extraZoom+"<="+(Ctx.INSTANCE.getOptions().getMaxZoom()+labelRange),
				"-each", "dots=4**("+step+")",
				"-each", "minzoom=minzoom+"+extraZoom,
				"-each", "maxzoom=maxzoom+"+extraZoom,
				"-each", "name=name+' +"+step+"'",
				"-dots",
					"dots",
					"evenness=1",
					"copy-fields=dots,label,minzoom,maxzoom,areaSqkm"+(fieldsToCopy.isEmpty()?"":(","+fieldsToCopy.stream().map(Field::name).collect(Collectors.joining(",")))),
					"multipart"
			).toFeatureCollection();
	    	log.info("Added {} label points at zoom shift {}", features.getFeatures().size(), step);
	    	result.getFeatures().addAll(features.getFeatures());
	    	return !features.getFeatures().isEmpty();
		}
	}
	

	private String minzoomJS() {
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
