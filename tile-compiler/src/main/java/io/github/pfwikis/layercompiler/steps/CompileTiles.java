package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.Tools;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompileTiles extends LCStep {
	
    @Override
    public LCContent process() throws Exception {
    	log.info("Compiling Tiles");
    	
    	
    	var layers = getInputs().entrySet()
    		.stream()
    		.map(e->Pair.of(e.getKey(), createTippecanoeProperties(e.getValue())))
    		.map(e->List.of("-L", new Runner.TmpGeojson(e.getKey()+":", LCContent.from(e.getValue()))))
    		.toList();

        var ttmp = new File(Runner.TMP_DIR, "tippecanoe-tmp").getAbsoluteFile().getCanonicalFile();
        ttmp.mkdirs();
        var tmpPMTiles = new File(ttmp, "golarion.pmtiles");

        Tools.tippecanoe(
        	null,
            "-z"+ctx.getOptions().getMaxZoom(),
            "--full-detail="+Math.max(14,32-ctx.getOptions().getMaxZoom()), //increase detail level on max-zoom
            // |
            // V does not work yet
            //"--generate-variable-depth-tile-pyramid", //does not add levels if the detail is already maxed
            //see https://github.com/maplibre/maplibre-gl-js/issues/5618
            "-n", "golarion",
            "-o", tmpPMTiles,
            "--force",
            "--detect-shared-borders",
            "--preserve-input-order",
            "-B", "0",
            "--coalesce-densest-as-needed",
            "-t", ttmp,
            layers
        );
        var finalOutput = new File(ctx.getOptions().targetDirectory(), "golarion.pmtiles");
        FileUtils.deleteQuietly(finalOutput);
        FileUtils.moveFile(tmpPMTiles, finalOutput);
        FileUtils.deleteDirectory(ttmp);
    	
    	
        return LCContent.empty();
    }
    
    
    private FeatureCollection createTippecanoeProperties(LCContent in) {
    	int maxzoom = ctx.getOptions().getMaxZoom();
    	var fc = in.toFeatureCollection();
    	//set tippecanoe based on filterMin/Maxzoom
    	for(var f:fc.getFeatures()) {
			f.getTippecanoe().setMinzoom(
				max(
					f.getTippecanoe().getMinzoom(),
					clamp(0, f.getProperties().getFilterMinzoom(), maxzoom)
				)
			);
		
			f.getTippecanoe().setMaxzoom(
				min(
					f.getProperties().getFilterMaxzoom(),
					f.getTippecanoe().getMaxzoom()
				)
			);
    	}

        return fc;
    }

    private static Integer max(Integer... values) {
    	Integer max = null;
		for(var v:values) {
			if(v != null && (max == null || max < v))
				max = v;
		}
		return max;
	}
    
    private static Integer min(Integer... values) {
		Integer min = null;
		for(var v:values) {
			if(v != null && (min == null || min > v))
				min = v;
		}
		return min;
	}
    
    private static Integer clamp(int min, Integer value, int max) {
		if(value == null) return null;
		return Math.clamp(value, min, max);
	}

}
