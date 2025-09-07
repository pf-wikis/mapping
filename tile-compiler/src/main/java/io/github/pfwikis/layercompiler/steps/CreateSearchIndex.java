package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.StringUtils;

import com.google.common.util.concurrent.AtomicDouble;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.run.Tools;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateSearchIndex extends LCStep {

	@Data
	@AllArgsConstructor
	private static class BBox {
		private double minLng;
		private double minLat;
		private double maxLng;
		private double maxLat;
	}
	
	record Category(String category, List<Result> entries) {}
	record Result(String label, double[] bbox, Double areaKm2) {}
	record BoxEntry(BBox box, AtomicDouble areaKm2) {}
	
    @Override
    public LCContent process() throws Exception {
    	log.info("Creating a searchIndex over "+getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining(", ")));
    	
    	var res = new ArrayList<Category>();
    	
    	for(var e:this.getInputs().entrySet()) {
    		var map = new HashMap<String, BoxEntry>();
    		FeatureCollection withArea;
    		if(e.getKey().equals("cities") || e.getKey().equals("locations")) {
    			withArea = e.getValue().toFeatureCollection();
    		} else {
    			withArea = Tools.qgis(this, "native:fieldcalculator", e.getValue(),
					"--FIELD_NAME=areaKm2",
					"--FIELD_TYPE=0", //double
					"--FORMULA=$area"
    			).toFeatureCollectionAndFinish();
    		}
    				
    		for(var f : withArea.getFeatures()) {
    			if(f.getProperties().getLabels() != null)
    				throw new IllegalStateException("Unresolved labels in layer "+e.getKey()+" in "+f.getProperties());

    			if(f.getProperties().getLabel() == null) continue;
    			if(StringUtils.isBlank(f.getProperties().getLabel().identifier())) throw new IllegalStateException("Strange label "+e.getKey()+" in "+f.getProperties());

    			var box = map.computeIfAbsent(f.getProperties().getLabel().identifier(), k->{
    				var p = f.getGeometry().streamPoints().findAny().get();
    				return new BoxEntry(new BBox(p.lng(), p.lat(), p.lng(), p.lat()), new AtomicDouble(0));
    			});
    			f.getGeometry().streamPoints().forEach(p->{
    				box.box.setMaxLat(Math.max(box.box.getMaxLat(), p.lat()));
    				box.box.setMinLat(Math.min(box.box.getMinLat(), p.lat()));
    				box.box.setMaxLng(Math.max(box.box.getMaxLng(), p.lng()));
    				box.box.setMinLng(Math.min(box.box.getMinLng(), p.lng()));
    			});
    			box.areaKm2.addAndGet(f.getProperties().getAreaKm2());
    		}
    		res.add(
    				new Category(e.getKey(),
    				map.entrySet().stream()
    				.map(b->new Result(b.getKey(), toArray(b.getValue().box), b.getValue().areaKm2.get()>0?b.getValue().areaKm2.get():null))
					.sorted(Comparator.comparing(Result::label))
					.toList())
    		);
    	}
    	
    	Collections.sort(res, Comparator.comparing(Category::category));
    	
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

	private double[] toArray(BBox box) {
		if(box.getMinLat()==box.getMaxLat() && box.getMinLng()==box.getMaxLng()) {
			return new double[] {box.getMinLng(), box.getMinLat()};
		}
		return new double[] {
			box.getMinLng(),
			box.getMinLat(),
			box.getMaxLng(),
			box.getMaxLat()
		};
	}
}

