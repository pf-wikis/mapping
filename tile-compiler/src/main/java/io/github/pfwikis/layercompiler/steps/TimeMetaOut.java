package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;

import com.google.common.collect.Range;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStepMergingTime;
import io.github.pfwikis.util.Jackson;

public class TimeMetaOut extends LCStepMergingTime {

    @Override
    public LCContent process(Inputs in) throws IOException {
    	var meta = in.getInput("meta").toFeatureCollection().getProperties().getTimeMeta();
    	var res = meta.getEntries()
    		.stream()
    		.map(e->new Res(
    			e.getId(),
    			toLabel(e.getTime()),
    			e.getTime().hasLowerBound()?e.getTime().lowerEndpoint():Integer.MIN_VALUE,
    			e.getTime().hasUpperBound()?e.getTime().upperEndpoint():Integer.MAX_VALUE
    		))
    		.toList();
    	
    	var f = new File(ctx.getOptions().targetDirectory(), "../gen/time-meta.json");
    	f.getParentFile().mkdirs();
    	Jackson.JSON.writeValue(f, res);
    	
    	return LCContent.empty();
    }
    
    private String toLabel(Range<Integer> time) {
		if(!time.hasLowerBound())
			return "before <a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(time.upperEndpoint());
		if(!time.hasUpperBound())
			return "since <a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(time.lowerEndpoint());
		
		int start = time.lowerEndpoint();
		int end = time.upperEndpoint()-1;
		if(start == end)
			return "<a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(start);
		
		return "<a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a> - <a href=\"https://pathfinderwiki.com/wiki/%2$d_AR\">%2$d AR</a>"
				.formatted(start, end);
	}

	private static record Res(
    	int id,
    	String label,
    	int start,
    	int end
    ) {}
}
