package io.github.pfwikis.layercompiler.description;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.CaseFormat;

import io.github.classgraph.ClassGraph;
import io.github.pfwikis.layercompiler.steps.*;
import io.github.pfwikis.layercompiler.steps.LCStep.Ctx;
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
        private Map<String, String> otherDependencies = new HashMap<>();

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

        @Override
        protected LCStep create() {
            return new ReadFile(file);
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

        @Override
        protected LCStep create() {
            try {
                return MAP.get(this.getStep()).getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
                throw new IllegalStateException();
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
