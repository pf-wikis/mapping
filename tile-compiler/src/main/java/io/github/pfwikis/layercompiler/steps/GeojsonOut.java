package io.github.pfwikis.layercompiler.steps;

import java.io.File;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.util.Jackson;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class GeojsonOut extends StepExecutor {
	
	private String filename;
	
    @Override
    public Content process(Inputs in) throws Exception {
    	var meta = in.getInput("time-meta").toFeatureCollection().getProperties().getTimeMeta();
    	var fc = in.getInput().toFeatureCollection();
    	fc = CompileTiles.applyTimeMeta(meta, fc);
    	Jackson.JSON.writeValue(
    		new File(Ctx.INSTANCE.getOptions().targetDirectory(), filename),
    		fc
    	);
        return Content.empty();
    }

}
