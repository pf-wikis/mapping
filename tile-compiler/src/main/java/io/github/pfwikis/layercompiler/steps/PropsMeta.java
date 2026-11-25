package io.github.pfwikis.layercompiler.steps;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import io.github.pfwikis.layercompiler.ColorPalette;
import io.github.pfwikis.layercompiler.description.Ctx;
import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.util.ColorUtil;
import io.github.pfwikis.util.Jackson;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

@Slf4j
@Time.Requirement(Time.Requirement.Value.REQUIRES_MERGED)
public class PropsMeta extends StepExecutor {

	@Setter
	private String filenameSuffix;
	
    @Override
    public Content process(Inputs in) throws IOException {
    	
    	var layers = new TreeMap<String, LayerMeta>();
    	in.getInputs().entrySet()
    		.stream()
			.map(e->Pair.of(e.getKey(), collectLayerInfo(e.getKey(), e.getValue().toFeatureCollection())))
			.sorted(Comparator.comparing(Pair::getKey))
			.forEach(e->layers.put(e.getKey(), e.getValue()));
    	
    	var sb = new StringBuilder();
    	sb
    		.append("export const maxZoomWithData = ")
    		.append(Integer.toString(Ctx.INSTANCE.getOptions().getMaxZoom()))
    		.append(";\n")
    		.append("export const Prop = {")
    		.append(layers.values().stream().flatMap(l->l.props.keySet().stream()).sorted().distinct().map(v->"\n  "+v+": '"+v+"',").collect(Collectors.joining("")))
    		.append("\n} as const;\n")
    		.append("export type Prop = typeof Prop[keyof typeof Prop];\n")
    		.append("export type ExistingLayer = ")
    		.append(layers.keySet().stream().map(v->"'"+v+"'").sorted().collect(Collectors.joining("|")))
    		.append(";\n")
    		.append("export const propsMeta = ")
    		.append(Jackson.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(layers))
    		.append(" as const;\n")
    		.append("export const colors = ")
    		.append(Jackson.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(
    			Arrays.stream(ColorPalette.values())
    				.filter(v->v.getColor()!=null)
    				.flatMap(v->Arrays.stream(v.getLayerNames())
    						.map(l->Map.entry(l, ColorUtil.toHex(v.getColor())))
    				)
    				.collect(Collectors.toMap(Entry::getKey, Entry::getValue))
    		))
    		.append(";\n");
    	
    	log.info("Props meta:\n{}", sb);
    	
    	Files.writeString(new File(Ctx.INSTANCE.getOptions().targetGenDirectory(), "props-meta-"+filenameSuffix+".ts").toPath(), sb);
    	
    	return Content.empty();
    }
    
    private LayerMeta collectLayerInfo(String id, FeatureCollection fc) {
    	var props = new HashMap<String, PropMeta>();
    	fc.getFeatures().forEach(f-> {
    		var json = Jackson.JSON.valueToTree(f.getProperties()).asObject();
    		for(var prop:json.properties()) {
    			props.computeIfAbsent(prop.getKey(), PropMeta::new).integrate(prop.getValue());
    		}
    		if(f.getProperties().getExport() != null) {
    			for(var prop:json.get(Properties.Fields.export).properties()) {
        			props.computeIfAbsent("export_"+prop.getKey(), PropMeta::new).integrate(prop.getValue());
        		}
    		}
    	});
    	
    	props.values().forEach(m->m.nullEntries = fc.getFeatures().size()-m.nonNullEntries);
    	var sortedProps = new LinkedHashMap<String, PropMeta>();
    	props.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).forEach(e->sortedProps.put(e.getKey(), e.getValue()));
    	
    	return new LayerMeta(
    		fc.getFeatures().stream().anyMatch(f->f.getProperties().getTimeIndexStart()!=null || f.getProperties().getTimeIndexEnd()!=null),
    		sortedProps
    	);
    }

    @Data
    private static class PropMeta {
		private final String name;
		private BigDecimal minNumber;
		private BigDecimal maxNumber;
		private int nonNullEntries = 0;
		private int nullEntries = 0;
		
		public void integrate(JsonNode value) {
			if(!value.isNull())
				nonNullEntries++;
			if(value.isNumber()) {
				var val = new BigDecimal(value.numberValue().toString());
				if(minNumber == null || minNumber.compareTo(val) > 0)
					minNumber = val;
				if(maxNumber == null || maxNumber.compareTo(val) < 0)
					maxNumber = val;
			}
		}
    }
    
	private static record LayerMeta(
    	boolean hasTime,
    	Map<String, PropMeta> props
    ) {}
}
