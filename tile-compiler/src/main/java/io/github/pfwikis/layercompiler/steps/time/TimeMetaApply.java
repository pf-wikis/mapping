package io.github.pfwikis.layercompiler.steps.time;

import java.io.IOException;
import java.util.Objects;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStepMergingTime;

public class TimeMetaApply extends LCStepMergingTime {

    @Override
    public LCContent process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	var meta = in.getInput("meta").toFeatureCollection().getProperties().getTimeMeta();
    	Objects.requireNonNull(meta);
    	fc.getFeatures().forEach(f-> {
    		f.getProperties().setTimeIndexStart(meta.getIndexForStart(f.getProperties().getTime()));
    		f.getProperties().setTimeIndexEnd(meta.getIndexForEnd(f.getProperties().getTime()));
    	});
    	return LCContent.from(fc);
    }
}
