package io.github.pfwikis.layercompiler.steps;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.model.Feature;
import io.github.pfwikis.model.FeatureCollection;
import io.github.pfwikis.model.Label;
import io.github.pfwikis.model.Properties;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@Setter
public class ResolveLabels extends LCStep {

	private String from;
	
    @Override
    public LCContent process() throws IOException {
    	var fc = getInput().toFeatureCollection();
    	var res = new FeatureCollection();
    	fc.getFeatures().forEach(f-> {
    		var labels = getLabelField(f.getProperties());
    		//no label
    		if(labels.isNull() || (labels.isTextual() && labels.asText().isBlank())) {
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
    	return LCContent.from(res);
    }

	private JsonNode getLabelField(Properties properties) {
		if(from == null)
			return Optional.ofNullable(properties.getLabels()).orElse(NullNode.getInstance());
		return properties.getUnknownFields()
				.getOrDefault(from, NullNode.getInstance());
	}

	private List<Label> splitLabels(JsonNode labels) {
		try {
			if(labels.isTextual()) {
				return Collections.singletonList(new Label(labels.textValue().trim(), null));
			}
			if(labels.isObject()) {
				return List.of(LCContent.MAPPER.treeToValue(labels, Label.class));
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
