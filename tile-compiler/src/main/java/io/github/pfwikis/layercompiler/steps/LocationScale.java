package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.Sets;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.util.Jackson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.ANY)
public class LocationScale extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	
    	//collect hashes first
    	var hash2Text = new HashMap<Integer, TreeSet<String>>();
    	fc.getFeatures().forEach(f-> {
    		var txt = toText(f);
    		f.getProperties().setText(txt);
    		hash2Text.computeIfAbsent(hash(txt), _->new TreeSet<>()).add(txt);
    	});
    	
    	//assign hashes as ids
    	var text2Id = new HashMap<String, Integer>();
    	fc.getFeatures().forEach(f -> {
    		var txt = f.getProperties().getText();
    		var hash = text2Id.computeIfAbsent(txt, _-> {
    			var hashCode = hash(txt);
        		var collisions = hash2Text.get(hashCode);
        		var index = Lists.newArrayList(collisions).indexOf(txt);
        		
        		if(index>0) {
        			while(hash2Text.containsKey(++hashCode)) {}
        			hash2Text.put(hashCode, Sets.newTreeSet(List.of(txt)));
        		}
        		return hashCode;
    		});
    		
    		f.getProperties().setFid((long)hash);
			f.getProperties().setText(null);
			f.getProperties().setLink(null);
    	});
    	
    	File dir = new File(Ctx.INSTANCE.getOptions().targetDirectory(), "extra");
    	dir.mkdirs();
    	for(int i=0;i<10;i++) {
    		int id = i;
    		var slice = new TreeMap<Integer, String>();
    		text2Id.entrySet().stream()
    			.filter(e->e.getValue()%10==id)
    			.sorted(Comparator.comparing(Entry::getValue))
    			.forEach(e->slice.put(e.getValue(), e.getKey()));
    		Jackson.JSON.writeValue(new File(dir, id+".json"), slice);
    	}
    	
    	return Content.derivedFrom(in, GeoData.from(fc));
    }

	private int hash(String txt) {
		return Math.abs(txt.hashCode());
	}

	private String toText(Feature f) {
		return "<h3><a href=\""+f.getProperties().getLink()+"\" target=\"_blank\">"
				+f.getProperties().getLabel()+"</a></h3>"+Optional.ofNullable(f.getProperties().getText()).orElse("");
	}
}
