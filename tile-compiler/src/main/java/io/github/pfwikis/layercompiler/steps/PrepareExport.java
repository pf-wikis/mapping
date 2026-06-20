package io.github.pfwikis.layercompiler.steps;

import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.layercompiler.steps.time.TimeMetaCollect.TimeMeta;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Properties.ExportProperties;
import io.github.pfwikis.util.time.TimeRange;

@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class PrepareExport extends StepExecutor {

	@Override
	protected Content process(Inputs in) throws Exception {
		var meta = in.getInput("time-meta").toFeatureCollection().getProperties().getTimeMeta();
    	var fc = in.getInput().toFeatureCollection();
    	applyTimeMeta(meta, fc);
    	cleanProperties(fc);
    	applyExport(this.getDescription().getGroup(), fc);
    	//addIds(fc);
    	return Content.derivedFrom(in, GeoData.from(fc));
	}
	
	private void addIds(FeatureCollection fc) {
		//there could be ids already so we want to make sure we only pick higher numbers
		long nextId = 1+fc.getFeatures().stream().filter(f->f.getProperties().getFid()!=null).mapToLong(f->f.getProperties().getFid()).max().orElse(-1);
		for(var f:fc.getFeatures()) {
			if(f.getProperties().getFid() == null)
				f.getProperties().setFid(nextId++);
		}
	}

	private void applyExport(String layer, FeatureCollection fc) {
		int maxZoom = Ctx.INSTANCE.getOptions().getMaxZoom();
		for(var f:fc.getFeatures()) {
			var ex = new ExportProperties();
			f.getProperties().setExport(ex);
			//filter by zoom level
			var fMinzoom = f.getProperties().getMinzoom();
			var fMaxzoom = f.getProperties().getMaxzoom();
			ex.setTileMinzoom(clamp(0, fMinzoom, maxZoom, 0));
			ex.setTileMaxzoom(clamp(0, fMaxzoom, maxZoom, maxZoom));
			
			if(fMinzoom!=null && fMinzoom==ex.getTileMinzoom() && fMinzoom>=0)
				f.getProperties().setMinzoom(null);
			if(fMaxzoom!=null && fMaxzoom==ex.getTileMaxzoom() && fMaxzoom <= maxZoom)
				f.getProperties().setMaxzoom(null);
		}
	}

    private static Integer clamp(int min, Integer value, int max, int defaultValue) {
		if(value == null) return defaultValue;
		return Math.clamp(value, min, max);
	}

	private void applyTimeMeta(TimeMeta meta, FeatureCollection fc) {
		boolean needsTimeIndex = fc.getFeatures().stream().anyMatch(f->!f.getProperties().getTime().equals(TimeRange.always()));
    	fc.getFeatures().forEach(f-> {
    		if(needsTimeIndex) {
	    		var time = f.getProperties().getTime();
	    		f.getProperties().setTimeIndexStart(meta.getIndexForStart(time));
	    		f.getProperties().setTimeIndexEnd(meta.getIndexForEnd(time));
    		}
    		f.getProperties().setTime(null);
    	});
	}


	private void cleanProperties(FeatureCollection fc) {
    	for(var f:fc.getFeatures()) {
    		f.getProperties().setTime(null);
    		f.getProperties().getUnknownFields().clear();
    	}
	}
}