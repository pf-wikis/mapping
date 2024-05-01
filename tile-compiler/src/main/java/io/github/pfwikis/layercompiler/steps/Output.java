package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.Files;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.Feature.Tippecanoe;
import lombok.extern.slf4j.Slf4j;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Properties;

@Slf4j
public class Output extends LCStep {

    private final ObjectWriter printer = LCContent.MAPPER.writer().withDefaultPrettyPrinter();

    @Override
    public LCContent process() throws IOException {
        var value = createTippecanoeProperties();

        byte[] result;
        if(!ctx.getOptions().isProdDetail()) {
            result = printer.writeValueAsBytes(value);
        }
        else {
            result = LCContent.MAPPER.writeValueAsBytes(value);
        }
        Files.write(result, new File(ctx.getGeo(), getName()+".geojson"));
        return LCContent.from(value);
    }

    private FeatureCollection createTippecanoeProperties() throws IOException {
    	int maxzoom = ctx.getOptions().getMaxZoom();
    	var fc = getInput().toFeatureCollection();
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
