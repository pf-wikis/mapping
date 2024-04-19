package io.github.pfwikis;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.dexecutor.core.DefaultDexecutor;
import com.github.dexecutor.core.DexecutorConfig;
import com.github.dexecutor.core.ExecutionConfig;

import io.github.pfwikis.layercompiler.description.LCDescription;
import io.github.pfwikis.layercompiler.steps.LCContent;
import io.github.pfwikis.layercompiler.steps.LCStep;
import io.github.pfwikis.layercompiler.steps.LCStep.Ctx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LayersCompiler {

    private final TileCompiler tileCompiler;
    private final File stepsFile = new File("steps.yaml");

    public void compile() throws Exception {
        var lsDescriptions = new ObjectMapper(new YAMLFactory()).readValue(stepsFile, LCDescription[].class);

        Map<String, LCStep> steps = new HashMap<>();
        var pool = Executors.newFixedThreadPool(4);

        try {
            var config = new DexecutorConfig<String, LCContent>(pool, id-> {
                return Objects.requireNonNull(steps.get(id), "Could not resolve step "+id);
            });
            var executor = new DefaultDexecutor<>(config);

            createSteps(lsDescriptions, steps, executor);

            var results = executor.execute(ExecutionConfig.TERMINATING);
            if(!results.getErrored().isEmpty()) {
                log.error("Failed at least some executions");
                System.exit(-1);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.DAYS);
        }
    }

    private void createSteps(LCDescription[] lsDescriptions, Map<String, LCStep> steps, DefaultDexecutor<String, LCContent> executor) {
        for(var lsDescription : lsDescriptions) {
            for(int i=0;i<lsDescription.getSteps().size();i++) {
                var step = lsDescription.getSteps().get(i);
                String name = lsDescription.createName(i);
                if(steps.containsKey(name)) {
                    throw new IllegalStateException("Duplicate name "+name);
                }

                var lcstep = step.create(
                    new Ctx(
                        tileCompiler.getOptions(),
                        tileCompiler.getGeo(),
                        tileCompiler.getOptions().getMappingDataFile()
                    )
                );
                lcstep.setName(lsDescription.getName());
                lcstep.setStep(step.getStep());
                steps.put(name, lcstep);
                executor.addIndependent(name);

                if(i>0) {
                    var dep = lsDescription.createName(i-1);
                    lcstep.getInputMapping().put("in", dep);
                    executor.addDependency(dep, name);
                }
                if(step.getDependsOn().getIn() != null) {
                    var dep = resolveName(step.getDependsOn().getIn(), lsDescriptions);
                    lcstep.getInputMapping().put("in", dep);
                    executor.addDependency(dep, name);
                }

                for(var e:step.getDependsOn().getOtherDependencies().entrySet()) {
                    var dep = resolveName(e.getValue(), lsDescriptions);
                    lcstep.getInputMapping().put(e.getKey(), dep);
                    executor.addDependency(dep, name);
                }
            }
        }
    }

    private String resolveName(String name, LCDescription[] lsDescriptions) {
        if(name.contains("."))
            return name;
        var descr = Arrays.stream(lsDescriptions).filter(lsd->lsd.getName().equals(name)).findAny().get();
        return descr.createName(descr.getSteps().size()-1);
    }
}
