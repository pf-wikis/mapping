package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.layercompiler.steps.time.TimeMetaCollect.TimeMeta;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Runner;
import io.github.pfwikis.run.Tools;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class CompileTiles extends StepExecutor {
	
	private String filename = "golarion";
	private String extension = "pmtiles";
	
    @Override
    public Content process(Inputs in) throws Exception {
    	log.info("Compiling Tiles");
    	
    	var meta = in.getInput("time-meta").toFeatureCollection().getProperties().getTimeMeta();
    	Objects.requireNonNull(meta);
    	
    	var layers = in.getInputs().entrySet()
    		.stream()
    		.filter(l->!l.getKey().equals("time-meta"))
    		.map(e->Pair.of(e.getKey(), applyTimeMeta(meta, createTippecanoeProperties(e.getValue()))))
    		.peek(e->cleanProperties(e.getValue()))
    		.map(e->List.of("-L", new Runner.TmpGeojson(e.getKey()+":", GeoData.from(e.getValue()))))
    		.toList();

        var out = Tools.tippecanoe(this, extension,
    		"-z"+Ctx.INSTANCE.getOptions().getMaxZoom(),
            "--full-detail="+Math.max(14,32-Ctx.INSTANCE.getOptions().getMaxZoom()), //increase detail level on max-zoom
            // |
            // V does not work yet
            //"--generate-variable-depth-tile-pyramid", //does not add levels if the detail is already maxed
            //see https://github.com/maplibre/maplibre-gl-js/issues/5618
            "--no-tile-size-limit",
            "-n", "golarion",
            "--force",
            "--detect-shared-borders",
            "--preserve-input-order",
            "-B", "0",
            "--coalesce-densest-as-needed",
            layers
        );
        
        var finalOutput = new File(Ctx.INSTANCE.getOptions().targetDirectory(), filename+"."+extension);
        FileUtils.deleteQuietly(finalOutput);
        FileUtils.writeByteArrayToFile(finalOutput, out.toBytes());
    	
        return Content.empty();
    }
    
    
    private FeatureCollection applyTimeMeta(TimeMeta meta, FeatureCollection fc) {
    	fc.getFeatures().forEach(f-> {
    		var time = f.getProperties().getTime();
    		f.getProperties().setTimeIndexStart(meta.getIndexForStart(time));
    		f.getProperties().setTimeIndexEnd(meta.getIndexForEnd(time));
    		f.getProperties().setTime(null);
    	});
    	return fc;
	}


	private void cleanProperties(FeatureCollection fc) {
    	for(var f:fc.getFeatures()) {
    		f.getProperties().setTime(null);
    	}
	}


	private FeatureCollection createTippecanoeProperties(GeoData in) {
    	int maxzoom = Ctx.INSTANCE.getOptions().getMaxZoom();
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
