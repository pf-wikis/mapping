package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset.Entry;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.ANY)
public class LocationTypeToIcon extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
    	var icons = Arrays.stream(new File("sprites").list())
    		.filter(n->n.startsWith("location-") && n.endsWith(".svg"))
    		.map(n->n.substring(9, n.length()-4))
    		.collect(Collectors.toSet());
    	
    	var counts = HashMultiset.<String>create();
    	var fc = in.getInput().toFeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		counts.add(f.getProperties().getType());
    		if(icons.contains(f.getProperties().getType())) {
    			f.getProperties().setIcon("location-"+f.getProperties().getType());
    		}
    		else {
    			f.getProperties().setIcon("location-other");
    		}
    		f.getProperties().setType(null);
    	});
    	
    	log.info(
			"Counted location types:\n{}",
	    	counts.entrySet()
	    		.stream()
	    		.sorted(Comparator.<Entry<String>, Integer>comparing(Entry::getCount).reversed())
	    		.map(e->e.getElement()+"\tx"+e.getCount())
	    		.collect(Collectors.joining("\n"))
		);
    	
    	return Content.derivedFrom(in, GeoData.from(fc));
    }
}
