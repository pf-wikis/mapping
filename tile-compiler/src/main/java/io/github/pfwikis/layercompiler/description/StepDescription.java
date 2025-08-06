package io.github.pfwikis.layercompiler.description;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.CaseFormat;

import io.github.classgraph.ClassGraph;
import io.github.pfwikis.layercompiler.steps.*;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import io.github.pfwikis.layercompiler.steps.model.LCStep.Ctx;
import lombok.Getter;
import lombok.Setter;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "step", visible = true, defaultImpl = StepDescription.Simple.class)
@JsonSubTypes({
    @Type(name = "READ_FILE", value = StepDescription.ReadFileStep.class),
    @Type(name = "ADD_ZOOM", value = StepDescription.AddZoomStep.class)
})
@Getter
@Setter
public abstract class StepDescription {

    private String step;
    private Dependencies dependsOn = new Dependencies();

    @Getter
    @Setter
    public static class Dependencies {
        private String in;
        @Getter(onMethod_=@JsonAnyGetter)
        private LinkedHashMap<String, String> otherDependencies = new LinkedHashMap<>();

        @JsonAnySetter
        public void addDependency(String name, String value) {
            otherDependencies.put(name, value);
        }

    }

    /*
     * GENERATE_BORDER_VARIANTS(GenerateBorderVariants::new),
     * ADD_CONTINENTS_BUFFER(AddContinentsBuffer::new),
     * ADD_DISTRICT_GAP(AddDistrictGap::new),
     * ADD_FRACTAL_DETAIL(AddFractalDetail::new), SMOOTH_LINES(SmoothLines::new),
     * SHAPE_RIVERS(ShapeRivers::new), STOP_PROCESSING(StopProcessing::new),
     * ADD_SCALE_AND_ZOOM(AddScaleAndZoom::new),
     * CREATE_TIPPECANOE_PROPERTY(CreateTippecanoeProperty::new),
     * PRETTY_PRINT(PrettyPrint::new);
     */

    @Getter
    @Setter
    public static class ReadFileStep extends StepDescription {
        private File file;
        private String layer;

        @Override
        protected LCStep create() {
            return new ReadFile(file, layer);
        }
    }

    @Getter
    @Setter
    public static class AddZoomStep extends StepDescription {
        private Integer minZoom;
        private Integer maxZoom;

        @Override
        protected LCStep create() {
            return new AddZoom(minZoom, maxZoom);
        }
    }

    @SuppressWarnings("unchecked")
    public static class Simple extends StepDescription {

    	private static final ObjectMapper OM = new ObjectMapper();
        private static Map<String, Class<? extends LCStep>> MAP = new HashMap<>();

        static {
            var caseConv=CaseFormat.UPPER_CAMEL.converterTo(CaseFormat.UPPER_UNDERSCORE);
            try (var scan =new ClassGraph()
                .enableClassInfo()
                .acceptPackages("io.github.pfwikis")
                .scan()) {

                scan.getSubclasses(LCStep.class).forEach(ci-> {
                    var type = ci.loadClass();
                    String name = StringUtils.removeStart(ci.getName(), ci.getPackageName()+".").replace("$", "_");
                    MAP.put(caseConv.convert(name), (Class<? extends LCStep>) type);
                });
            }
        }
        
        private Map<String, JsonNode> unknownFields = new HashMap<>();
        @JsonAnySetter
        public void setOtherField(String name, JsonNode value) {
            unknownFields.put(name, value);
        }

        @Override
        protected LCStep create() {
            try {
            	var type = MAP.get(this.getStep());
            	if(type == null) {
            		throw new IllegalArgumentException("Can't resolve class "+this.getStep());
            	}
                var step = type.getConstructor().newInstance();
                if(!unknownFields.isEmpty())
                	step = OM.readerForUpdating(step).readValue((ObjectNode)OM.valueToTree(unknownFields));
                return step;
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException | IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }


    public LCStep create(Ctx ctx) {
        var result = create();
        result.init(ctx);
        return result;
    }

    protected abstract LCStep create();
}
