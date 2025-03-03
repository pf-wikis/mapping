package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.StringUtils;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import lombok.extern.slf4j.Slf4j;
import mil.nga.geopackage.BoundingBox;

@Slf4j
public class CreateSearchIndex extends LCStep {

	record Result(String label, double[] bbox) {}
	
    @Override
    public LCContent process() throws Exception {
    	log.info("Creating a searchIndex over "+getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining(", ")));
    	
    	var map = new HashMap<String, BoundingBox>();
    	for(var e:this.getInputs().entrySet()) {
    		
    		for(var f : e.getValue().toFeatureCollection().getFeatures()) {
    			if(StringUtils.isEmpty(f.getProperties().getName())) continue;
    			
    			var box = map.computeIfAbsent(f.getProperties().getName(), k->{
    				var p = f.getGeometry().streamPoints().findAny().get();
    				return new BoundingBox(p.lng(), p.lat(), p.lng(), p.lat());
    			});
    			f.getGeometry().streamPoints().forEach(p->{
    				box.setMaxLatitude(Math.max(box.getMaxLatitude(), p.lat()));
    				box.setMinLatitude(Math.min(box.getMinLatitude(), p.lat()));
    				box.setMaxLongitude(Math.max(box.getMaxLongitude(), p.lng()));
    				box.setMinLongitude(Math.min(box.getMinLongitude(), p.lng()));
    			});	
    		}

    	}
    	
    	var res = map.entrySet()
    		.stream()
    		.sorted(Comparator.comparing(e->e.getKey()))
    		.map(e->new Result(e.getKey(), new double[] {
				e.getValue().getMinLongitude(),
				e.getValue().getMinLatitude(),
				e.getValue().getMaxLongitude(),
				e.getValue().getMaxLatitude()
    		}))
    		.toList();
    	LCContent.MAPPER.writeValue(new File(this.getCtx().getOptions().targetDirectory(), "search.json"), res);
    	var t = this.getCtx().getOptions().targetDirectory();
    	try(
    			var raw = new FileOutputStream(new File(t, "search.json"));
    			var gz = new GZIPOutputStream(new FileOutputStream(new File(t, "search.json.gz")));
    			var out = new TeeOutputStream(raw, gz)
    	) {
    		LCContent.MAPPER.writeValue(out, res);
    	}
    	
    	return LCContent.empty();
    }
}

