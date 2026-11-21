package io.github.pfwikis.layercompiler.steps.model;

import java.io.Closeable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import com.github.dexecutor.core.task.Task;
import com.google.common.base.Stopwatch;

import io.github.pfwikis.layercompiler.cache.CacheManager;
import io.github.pfwikis.layercompiler.description.StepDescription;
import io.github.pfwikis.layercompiler.steps.model.Time.ContentState;
import io.github.pfwikis.layercompiler.steps.model.Time.DataState;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent;
import io.github.pfwikis.layercompiler.steps.model.content.TimeSlicedContent.TimeSlice;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.util.TimeMap;
import io.github.pfwikis.util.TimeSet;
import io.github.pfwikis.util.time.TimeRange;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter @Setter
public abstract class StepExecutor extends Task<String, Content> {

    protected StepDescription description;
    private SequencedMap<String, String> inputMapping = new LinkedHashMap<>();
    private Map<String, Duration> subTimings = new HashMap<>();


    protected abstract Content process(Inputs in) throws Exception;


    public static void setThreadName(String group, String step, String variant) {
    	if(variant != null)
    		Thread.currentThread().setName(group+"."+step+"."+variant);
    	else
    		Thread.currentThread().setName(group+"."+step);
    }

    @Override
    public Content execute() {
    	var oldName = Thread.currentThread().getName();
        try {
        	setThreadName(description.getGroup(), description.getStep(), null);

        	String cacheKey = null;
        	try {
        		cacheKey = CacheManager.INSTANCE.keyFor(this);
        		if(cacheKey != null) {
        			var cached = CacheManager.INSTANCE.load(cacheKey);
        			if(cached != null)
        				return cached;
        		}
        	} catch(Exception e) {
        		log.warn("Could not use persistent cache for {}: {}", description.getId(), e.getMessage());
        		cacheKey = null;
        	}

        	//we need to differentiate between different cases based on our requirement
        	Content results = switch(description.getTimeRequirement()) {
        		case ANY,REQUIRES_MERGED -> executeWithMergedContents();
        		case REQUIRES_SLICED -> executeWithSlicedInputs();
        	};

        	if(cacheKey != null)
        		CacheManager.INSTANCE.store(cacheKey, results);

        	return results;
        } catch (Throwable t) {
        	log.error("Failed execution", t);
            throw new RuntimeException("Failed execution with: "+t.getMessage(), t);
        } finally {
        	Thread.currentThread().setName(oldName);
        }
    }

    private Content executeWithSlicedInputs() throws Exception {
		var variants = createSlicedVariants();
		
		//shortcut in case there is only one
		if(variants.size()==1) {
			log.info("Processing as sliced, but only one slice");
			var variant = variants.getFirst();
			var res = limitContentToTime(process(variant), variant.getTime());
			return Content.sliced(mergeIdenticalSlices(new ArrayList<>(res.getSlices())))
				.asTimelessIfPossible();
		}
		
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			var futures = variants
				.stream()
				.map(v-> executor.submit(() -> {
					StepExecutor.setThreadName(description.getGroup(), description.getStep(), v.getTime().toString());
					return limitContentToTime(process(v),v.getTime());
				}))
				.toList();
			log.info("Processing as sliced: {} slices", futures.size());
			var results = new ArrayList<TimeSlice>(futures.size());
		    try {
		        for (var f : futures) {
		            results.addAll(f.get().getSlices());
		        }
		        //the step could have produced the same result from different inputs
		        results = mergeIdenticalSlices(results);
                log.info(
                        "Processing as sliced: {} slices resulted in {} slices",
                        futures.size(),
                        results.size());
		        return Content.sliced(results);
		    } catch (Exception e) {
		    	futures.forEach(f->f.cancel(true));
		    	throw e;
		    }
		}
	}
    
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final Comparator<TimeRange> TIME_RANGE_ORDER = Comparator
        .comparing(TimeRange::getTimeStart, Comparator.nullsFirst(Comparator.naturalOrder()))
        .thenComparing(TimeRange::getTimeEnd, Comparator.nullsLast(Comparator.naturalOrder()));

    /*package*/ static ArrayList<TimeSlice> mergeIdenticalSlices(ArrayList<TimeSlice> results) {
        if(results.size() < 2)
            return results;

        results.sort(Comparator.comparing(TimeSlice::getTime, TIME_RANGE_ORDER));

        var merged = new ArrayList<TimeSlice>(results.size());
        var current = results.getFirst();
        var currentBytes = contentBytes(current.getData());
        for(int i=1;i<results.size();i++) {
            var next = results.get(i);
            var nextBytes = next.getData() == current.getData()
                ? currentBytes
                : contentBytes(next.getData());
            //Only contiguous ranges may be merged. Non-contiguous equal slices have to stay separate.
            if(Arrays.equals(currentBytes, nextBytes)
                    && current.getTime().toGuavaRange().isConnected(next.getTime().toGuavaRange())) {
                current = new TimeSlice(
                    TimeRange.from(current.getTime().toGuavaRange().span(next.getTime().toGuavaRange())),
                    current.getData()
                );
            }
            else {
                merged.add(current);
                current = next;
                currentBytes = nextBytes;
            }
        }
        merged.add(current);
        return merged;
    }

    private static byte[] contentBytes(GeoData data) {
        return data.isEmpty()? EMPTY_BYTES : data.toBytes();
    }

	private TimeSlicedContent limitContentToTime(Content content, TimeRange range) {
    	if(range.equals(TimeRange.always())) {
    		return content.asSliced();
    	}
		return Content.sliced(content.asSliced()
			.getSlices()
			.stream()
			.map(sl->new TimeSlice(sl.getTime().intersection(range), sl.getData()))
			.toList());
    }

	private Content executeWithMergedContents() throws Exception {
		Map<String, GeoData> inputs = new LinkedHashMap<>();
		var state = DataState.TIMELESS;
		for(var e:getAllInputs(Content::asMergedOrTimeless)) {
			if(e.getValue().getTimeState() == ContentState.MERGED)
				state = DataState.MERGED;
			inputs.put(e.getKey(), e.getValue().getData());
		}
		var in = Inputs.from(state, TimeRange.always(), inputs);
		return process(in);
	}

	protected List<Inputs> createSlicedVariants() {
		//special case for no inputs
		if(inputMapping.isEmpty())
			return List.of(Inputs.from(DataState.TIMELESS, TimeRange.always(), Collections.emptyMap()));
		
		var allInputs = getAllInputs(Content::asSliced);
		var allCoveredTime = TimeSet.create();
		allInputs.forEach((e)->e.getValue().getSlices().forEach(s->allCoveredTime.add(s.getTime())));
		
		return allInputs.stream()
			.map(in->createSlicedVariants(allCoveredTime, in.getKey(), in.getValue()))
			.reduce(this::mergeVariants).get()
			.values();
	}
	
	private TimeMap<Inputs> mergeVariants(TimeMap<Inputs> a, TimeMap<Inputs> b) {
		var result = TimeMap.<Inputs>create();
		for(var right:b.entries()) {
			for(var leftMatch:a.subTimeMap(right.getKey()).entries()) {
				var variant = Inputs.from(DataState.TIMELESS, leftMatch.getKey());
				variant.getInputs().putAll(leftMatch.getValue().getInputs());
				variant.getInputs().putAll(right.getValue().getInputs());
				result.put(leftMatch.getKey(), variant);
			}
		}
		return result;
	}
	
	private TimeMap<Inputs> createSlicedVariants(TimeSet allCoveredTime, String key, TimeSlicedContent slices) {
		var result=TimeMap.<Inputs>create();
		for(var slice:slices.getSlices()) {
			if(!result.subTimeMap(slice.getTime()).isEmpty())
				throw new IllegalStateException("This would create and error. We do not support overlapping ranges here.");
			result.put(slice.getTime(), Inputs.from(DataState.TIMELESS, key, slice));
		}
		
		var missing = TimeSet.create(allCoveredTime);
		missing.removeAll(result.ranges());
		for(var time:missing.asRanges()) {
			result.put(time, Inputs.from(DataState.TIMELESS, key, time, GeoData.from(new FeatureCollection())));
		}
		return result;
	}

	private <T extends Content> List<Pair<String, T>> getAllInputs(Function<Content, T> transform) {
		var res = new ArrayList<Pair<String, T>>(inputMapping.size());
		for(var e:inputMapping.entrySet()) {
			var key = e.getKey();
			var content = getResult(e.getValue());
			res.add(Pair.of(key, transform.apply(content.getResult())));
		}
		return res;
	}

	/**
	 * Inputs in the order they were declared, used for persistent cache keys.
	 */
	public List<Pair<String, Content>> getInputsForCaching() {
		return getAllInputs(Function.identity());
	}

	/**
	 * Steps that read files outside the DAG (for example READ_FILE) can add the content hash of
	 * those files to their cache key by overriding this method.
	 */
	public byte[] getCacheSalt() {
		return null;
	}

	/**
	 * Steps that write output files in addition to returning content must opt out of caching,
	 * unless a cache hit can reproduce those side effects as well.
	 */
	public boolean isCacheable() {
		return true;
	}
	
	public record Timing(String key, Stopwatch watch, StepExecutor step) implements Closeable {
		@Override
		public void close() {
			var time = watch.stop().elapsed();
			synchronized(step.subTimings) {
				step.subTimings.merge(key, time, Duration::plus);
			}
		}
    }
    public Timing measureSubtime(String key) {
    	return new Timing(key, Stopwatch.createStarted(), this);
    }


	public List<StepExecutor> createAutoSteps() {return Collections.emptyList();}
}
