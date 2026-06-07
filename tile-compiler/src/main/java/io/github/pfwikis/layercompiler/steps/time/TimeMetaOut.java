package io.github.pfwikis.layercompiler.steps.time;

import java.io.File;
import java.io.IOException;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.util.Jackson;
import io.github.pfwikis.util.time.TimeRange;

@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class TimeMetaOut extends StepExecutor {

    @Override
    public Content process(Inputs in) throws IOException {
    	var meta = in.getInput("meta").toFeatureCollection().getProperties().getTimeMeta();
    	var res = meta.getEntries()
    		.stream()
    		.map(e->new Res(
    			e.getId(),
    			toLabel(e.getTime()),
    			e.getTime().hasLowerBound()?e.getTime().getTimeStart():null,
    			e.getTime().hasUpperBound()?e.getTime().getTimeEnd():null
    		))
    		.toList();
    	
    	var f = new File(Ctx.INSTANCE.getOptions().targetDirectory(), "../gen/time-meta.json");
    	f.getParentFile().mkdirs();
    	Jackson.JSON.writeValue(f, res);
    	
    	return Content.empty();
    }
    
    private String toLabel(TimeRange time) {
    	if(!time.hasLowerBound() && !time.hasUpperBound()) {
    		throw new IllegalStateException();
    	}
		if(!time.hasLowerBound())
			return "before <a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(time.getTimeEnd());
		if(!time.hasUpperBound())
			return "since <a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(time.getTimeStart());
		
		int start = time.getTimeStart();
		int end = time.getTimeEnd()-1;
		if(start == end)
			return "<a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a>"
					.formatted(start);
		
		return "<a href=\"https://pathfinderwiki.com/wiki/%1$d_AR\">%1$d AR</a> - <a href=\"https://pathfinderwiki.com/wiki/%2$d_AR\">%2$d AR</a>"
				.formatted(start, end);
	}

	private static record Res(
    	int id,
    	String label,
    	Integer start,
    	Integer end
    ) {}
}
