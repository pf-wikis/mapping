package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

	record Category(String category, List<Result> entries) {}
	record Result(String label, double[] bbox) {}
	record BoxEntry(BoundingBox box, Set<String> source) {
		public String category() {
			if(source.size()>1)
				return "mixed";
			if(source.size()==0)
				return "none";
			return source.iterator().next();
		}
	}
	
    @Override
    public LCContent process() throws Exception {
    	log.info("Creating a searchIndex over "+getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining(", ")));
    	
    	var map = new HashMap<String, BoxEntry>();
    	for(var e:this.getInputs().entrySet()) {
    		
    		for(var f : e.getValue().toFeatureCollection().getFeatures()) {
    			if(f.getProperties().getLabels() != null)
    				throw new IllegalStateException("Unresolved labels in layer "+e.getKey()+" in "+f.getProperties());

    			if(f.getProperties().getLabel() == null) continue;
    			if(StringUtils.isBlank(f.getProperties().getLabel().identifier())) throw new IllegalStateException("Strange label "+e.getKey()+" in "+f.getProperties());

    			var box = map.computeIfAbsent(f.getProperties().getLabel().identifier(), k->{
    				var p = f.getGeometry().streamPoints().findAny().get();
    				return new BoxEntry(new BoundingBox(p.lng(), p.lat(), p.lng(), p.lat()), new HashSet<>());
    			});
    			box.source.add(e.getKey());
    			f.getGeometry().streamPoints().forEach(p->{
    				box.box.setMaxLatitude (Math.max(box.box.getMaxLatitude(), p.lat()));
    				box.box.setMinLatitude (Math.min(box.box.getMinLatitude(), p.lat()));
    				box.box.setMaxLongitude(Math.max(box.box.getMaxLongitude(), p.lng()));
    				box.box.setMinLongitude(Math.min(box.box.getMinLongitude(), p.lng()));
    			});	
    		}

    	}
    	
    	var res = map.entrySet()
    		.stream()
    		//group by category
    		.collect(Collectors.groupingBy(e->e.getValue().category()))
    		.entrySet()
    		.stream()
    		.map(e->new Category(
				e.getValue().getFirst().getValue().category(),
				e.getValue().stream()
					.map(b->new Result(b.getKey(), toArray(b.getValue().box)))
					.sorted(Comparator.comparing(Result::label))
					.toList()
    		))
    		.sorted(Comparator.comparing(Category::category))
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

	private double[] toArray(BoundingBox box) {
		if(box.getMinLatitude()==box.getMaxLatitude() && box.getMinLongitude()==box.getMaxLongitude()) {
			return new double[] {box.getMinLongitude(), box.getMinLatitude()};
		}
		return new double[] {
			box.getMinLongitude(),
			box.getMinLatitude(),
			box.getMaxLongitude(),
			box.getMaxLatitude()
		};
	}
}

