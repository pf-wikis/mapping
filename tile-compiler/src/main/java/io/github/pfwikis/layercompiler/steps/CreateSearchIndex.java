package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.output.TeeOutputStream;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.TreeRangeMap;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStepMergingTime;
import io.github.pfwikis.layercompiler.steps.time.TimeMetaCollect.TimeMeta;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.LngLat;
import io.github.pfwikis.run.Tools;
import io.github.pfwikis.util.Jackson;
import io.github.pfwikis.util.time.TimeRange;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateSearchIndex extends LCStepMergingTime {

	record Category(String category, List<Result> entries) {}
	record Result(String label, List<TimedResult> timed) {}
	record TimedResult(TimeRange timeYear, TimeRange timeIndex, @JsonUnwrapped Data data) {}
	
	/*must be object for proper equals*/
	@lombok.Data
	@AllArgsConstructor
	@NoArgsConstructor
	private static class Data {
		private double[] bbox;
		private Double areaM2;
	}
	
    @Override
    public LCContent process(Inputs in) throws Exception {
    	var meta = in.getInput("time-meta").toFeatureCollection().getProperties().getTimeMeta();
    	log.info("Creating a searchIndex over "+in.getInputs().entrySet().stream().map(e->e.getKey()).collect(Collectors.joining(", ")));
    	
    	var res = new ArrayList<Category>();
    	
    	for(var e:in.getInputs().entrySet()) {
    		if(e.getKey().equals("time-meta")) continue;
    		res.add(new Category(e.getKey(), processLayer(meta, e.getKey(), e.getValue())));
    	}
    	Collections.sort(res, Comparator.comparing(Category::category));
    	
    	Jackson.JSON.writeValue(new File(this.getCtx().getOptions().targetDirectory(), "search.json"), res);
    	var t = this.getCtx().getOptions().targetDirectory();
    	try(
    			var raw = new FileOutputStream(new File(t, "search.json"));
    			var gz = new GZIPOutputStream(new FileOutputStream(new File(t, "search.json.gz")));
    			var out = new TeeOutputStream(raw, gz)
    	) {
    		Jackson.JSON.writeValue(out, res);
    	}
    	
    	return LCContent.empty();
    }

	private ArrayList<Result> processLayer(TimeMeta meta, String layerName, LCContent content) throws IOException {
		FeatureCollection withArea;
		if(layerName.equals("cities") || layerName.equals("locations")) {
			withArea = content.toFeatureCollection();
		} else {
			withArea = Tools.qgis(this, "native:fieldcalculator", content,
				"--FIELD_NAME=areaM2",
				"--FIELD_TYPE=0", //double
				"--FORMULA=$area"
			).toFeatureCollection();
		}
		
		var perId = withArea.getFeatures()
			.stream()
			.filter(f->f.getProperties().getLabel()!=null)
			.peek(f-> {
				if(
					f.getProperties().getLabels() != null
					|| StringUtils.isBlank(f.getProperties().getLabel().identifier())
				)
					throw new IllegalStateException("Unresolved labels in layer "+layerName+" in "+f.getProperties());
			})
			.collect(Collectors.groupingBy(
				f->f.getProperties().getLabel().identifier(),
				Collectors.groupingBy(f->f.getProperties().getTime())
			));
		
		var results = new ArrayList<Result>();
		for(var idEntry:perId.entrySet()) {
			var id = idEntry.getKey();
			var timedResults = TreeRangeMap.<Integer, Data>create();
			
			for(var e:idEntry.getValue().entrySet()) {
				var time = e.getKey();
				var lngInfo = e.getValue().stream().flatMap(f->f.getGeometry().streamPoints()).collect(Collectors.summarizingDouble(LngLat::lng));
				var latInfo = e.getValue().stream().flatMap(f->f.getGeometry().streamPoints()).collect(Collectors.summarizingDouble(LngLat::lat));
				var areaM2 = e.getValue().stream().map(f->f.getProperties().getAreaM2()).filter(Objects::nonNull).findAny().orElse(null);
				timedResults.putCoalescing(time.toGuavaRange(), new Data(
					toArray(lngInfo, latInfo),
					areaM2
				));
			}
			
			results.add(new Result(
				id,
				timedResults.asMapOfRanges()
					.entrySet()
					.stream()
					.map(e->{
						var time = TimeRange.from(e.getKey());
						return new TimedResult(
							time,
							new TimeRange(meta.getIndexForStart(time), meta.getIndexForEnd(time)),
							e.getValue()
						);
					})
					.sorted(Comparator.comparing(tr->tr.timeIndex.getTimeStart()))
					.toList()
			));
		}
		results.sort(Comparator.comparing(Result::label));
		return results;
	}

	private double[] toArray(DoubleSummaryStatistics lng, DoubleSummaryStatistics lat) {
		if(lat.getMin()==lat.getMax() && lng.getMin()==lng.getMax()) {
			return new double[] {lng.getMin(), lat.getMin()};
		}
		return new double[] {
			lng.getMin(),
			lat.getMin(),
			lng.getMax(),
			lat.getMax()
		};
	}
}
