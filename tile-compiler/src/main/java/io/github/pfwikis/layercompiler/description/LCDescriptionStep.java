package io.github.pfwikis.layercompiler.description;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.Strings;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.google.common.base.CaseFormat;

import io.github.classgraph.ClassGraph;
import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.util.Jackson;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.node.ObjectNode;

@Getter
@Setter
public class LCDescriptionStep {

    private String step;
    private LinkedHashMap<String, String> dependsOn = new LinkedHashMap<String, String>();
    @JsonAnySetter
    private ObjectNode unknownFields = Jackson.JSON.getNodeFactory().objectNode();
    
    public StepExecutor createStep() {
    	try {
        	var type = TYPE_MAP.get(this.getStep());
        	if(type == null) {
        		throw new IllegalArgumentException("Can't resolve class "+this.getStep());
        	}
        	var instance = Jackson.JSON.treeToValue(unknownFields, type);
            
            return Objects.requireNonNull(instance);
        } catch (IllegalArgumentException | SecurityException e) {
            throw new IllegalStateException(e);
        }
    }
    
    private static Map<String, Class<? extends StepExecutor>> TYPE_MAP = new HashMap<>();
    static {
        var caseConv=CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.UPPER_UNDERSCORE);
        try (var scan =new ClassGraph()
            .enableClassInfo()
            .acceptPackages("io.github.pfwikis")
            .scan()) {

            scan.getSubclasses(StepExecutor.class).forEach(ci-> {
                var type = ci.loadClass();
                String name = Strings.CS.removeStart(ci.getName(), ci.getPackageName()+".").replace("$", "_");
                TYPE_MAP.put(caseConv.convert(name), (Class<? extends StepExecutor>) type);
            });
        }
    }
}
