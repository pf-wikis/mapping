package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.nio.file.Files;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.Jackson;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class GeojsonOutPerLabel extends StepExecutor {
	
	private String dirname;
	
    @Override
    public Content process(Inputs in) throws Exception {
    	var dir = new File(Ctx.INSTANCE.getOptions().targetDirectory(), dirname);
    	dir.mkdirs();
    	var fc = in.getInput().toFeatureCollection();
    	var labels = fc.getFeatures().stream().map(f->f.getProperties().getLabel().getLabel()).distinct().sorted().toList();
    	
    	for(var label:labels) {
    		var lfc = new FeatureCollection();
    		fc.getFeatures().stream()
    			.filter(f->f.getProperties().getLabel().equals(label))
    			.forEach(lfc.getFeatures()::add);
	    	Jackson.JSON.writeValue(
	    		new File(dir, label+".geojson"),
	    		lfc
	    	);
    	}
    	
    	Files.writeString(
    		Ctx.INSTANCE.getOptions().targetGenDirectory().toPath().resolve("highlight_labels.ts"),
    		"export const highlightLabels = "+Jackson.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(labels).replace("\", \"", "\",\n\t\"")
    	);
        return Content.empty();
    }

}
