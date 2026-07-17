package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import io.github.pfwikis.layercompiler.steps.model.Inputs;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.Time;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Label;
import io.github.pfwikis.model.Properties;
import io.github.pfwikis.util.Jackson;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.NullNode;

@Slf4j
@Getter @Setter
@Time.Requirement(Time.Requirement.Value.ANY)
public class ResolveLabels extends StepExecutor {

	private String from;
	
    @Override
    public Content process(Inputs in) throws IOException {
    	var fc = in.getInput().toFeatureCollection();
    	var res = new FeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		var labels = getLabelField(f.getProperties());
    		//no label
    		if(labels.isNull() || (labels.isString() && labels.stringValue().isBlank())) {
    			var c = f.<Feature>copy();
    			c.getProperties().setLabels(null);
    			res.getFeatures().add(c);
    		} else {
	    		for(Label l:splitLabels(labels)) {
	    			var c = f.<Feature>copy();
	    			c.getProperties().setLabels(null);
	    			c.getProperties().setLabel(l);
	    			res.getFeatures().add(c);
	    		}
    		}
    	});
    	return Content.derivedFrom(in, GeoData.from(res));
    }

	private JsonNode getLabelField(Properties properties) {
		if(from == null)
			return Optional.ofNullable(properties.getLabels()).orElse(NullNode.getInstance());
		return properties.getUnknownFields()
				.getOrDefault(from, NullNode.getInstance());
	}

	private List<Label> splitLabels(JsonNode labels) {
		try {
			if(labels.isString()) {
				var str = labels.stringValue().trim();
				var first = str.charAt(0);
				if(first == '[' || first == '{' || first == '"') {
					try {
						return splitLabels(Jackson.JSON.readTree(str));
					} catch(Exception e) {
						log.warn("Strange label `{}`", str);
					}
				}
				return Collections.singletonList(new Label(str, null));
			}
			if(labels.isObject()) {
				return List.of(Jackson.JSON.treeToValue(labels, Label.class));
			}
			if(labels.isArray()) {
				return StreamSupport.stream(labels.spliterator(), false)
						.flatMap(e->splitLabels(e).stream())
						.toList();
			}
			log.error("Can't parse JSON label `{}`", labels);
			return Collections.singletonList(new Label(labels.toString().trim(), null));
		} catch(Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
