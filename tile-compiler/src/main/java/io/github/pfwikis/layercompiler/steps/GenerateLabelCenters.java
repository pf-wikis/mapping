package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.FeatureCollection;
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

        var withZoom = Tools.mapshaper(this, withArea,
    		"-filter", "Name != null",
    		dissolve?List.of("-dissolve", "Name", "copy-fields=inSubregion,areaSqkm"):List.of(),
    		"-each", "tmpMercatorScaling="+mercatorScaling("this.centroidY"),
    		"-each", "tmpAdjustedSize=Math.sqrt(areaSqkm)*tmpMercatorScaling",
			"-each", "filterMinzoom="+filterMinzoom(this.getName()),
            "-each", "filterMaxzoom=filterMinzoom+"+LABEL_RANGE,
            "-each", "bufferSize=Math.sqrt(areaSqkm)*0.0002"	
		);
        withArea.finishUsage();
        
        if(!generateSubLabels) {
        	return Tools.qgis(this, "native:poleofinaccessibility", withZoom, "--TOLERANCE=1e-06");
        }
        
        
        
        //buffer first to fill gaps in highly distributed forests
        var buffered = Tools.qgis(this, "native:buffer", withZoom,
            "--DISTANCE=field:bufferSize",
            "--END_CAP_STYLE=0", "--JOIN_STYLE=0", "--MITER_LIMIT=2",
            "--DISSOLVE=field:Name");
        
        var mainPointsRes = Tools.qgis(this, "native:poleofinaccessibility", buffered, "--TOLERANCE=1e-06");
        buffered.finishUsage();
        var mainPoints = mainPointsRes.toFeatureCollection();
        mainPointsRes.finishUsage();
        
        
        
        
        //add the primary point to max filterzoom
        FeatureCollection result = new FeatureCollection();
        result.setFeatures(new ArrayList<>());
        add(result, mainPoints);
        /*
        for(int i=0;true;i++) {
        	var changed=addLowerZoomLabels(result, withZoom, i+1);
        	if(!changed) {
        		break;
        	}
        } 
        */
        withZoom.finishUsage();
        return LCContent.from(result);
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
    	add(result, features);
    	reduced.finishUsage();
    	return !features.getFeatures().isEmpty();
	}

	private void add(FeatureCollection result, FeatureCollection toAdd) {
    	for(var f : toAdd.getFeatures()) {
    		result.getFeatures().add(f);
    	}
	}

	private String filterMinzoom(String name) {
		if(forceMinzoom != null)
			return Integer.toString(forceMinzoom);

        return "Math.floor(Math.log2(20000/(Math.sqrt(areaSqkm)*%s)))"
        	.formatted(mercatorScaling("this.centroidY"));
    }

	private String mercatorScaling(String lat) {
		return "1/Math.cos(%s/180*Math.PI)"
			.formatted(lat);
	}
}
