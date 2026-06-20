package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.util.Jackson;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Time.Requirement(Time.Requirement.Value.ANY)
public class LocationScale extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	var nextId = new AtomicLong(0);
    	var texts = new HashMap<String, Long>();
    	fc.getFeatures().forEach(f-> {
    		if(f.getProperties().getText() != null) {
    			String text = "<h3><a href=\""+f.getProperties().getLink()+"\" target=\"_blank\">"
    					+f.getProperties().getLabel()+"</a></h3>"+f.getProperties().getText();
    			long id = texts.computeIfAbsent(text, _->nextId.getAndIncrement());
    			f.getProperties().setFid(id);
    			f.getProperties().setText(null);
    			f.getProperties().setLink(null);
    		}
    	});
    	
    	var textsRev = texts.entrySet().stream().collect(Collectors.toMap(
			Entry::getValue,
			Entry::getKey
    	));
    	
    	File dir = new File(Ctx.INSTANCE.getOptions().targetDirectory(), "extra");
    	dir.mkdirs();
    	for(int i=0;i<10;i++) {
    		int id = i;
    		var slice = textsRev.entrySet().stream().filter(e->e.getKey()%10==id).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    		Jackson.JSON.writeValue(new File(dir, id+".json"), slice);
    	}
    	
    	return Content.derivedFrom(in, GeoData.from(fc));
    }
}
