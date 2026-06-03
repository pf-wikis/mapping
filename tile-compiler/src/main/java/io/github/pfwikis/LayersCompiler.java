package io.github.pfwikis;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import tools.jackson.dataformat.yaml.YAMLMapper;

import com.github.dexecutor.core.DefaultDexecutor;
import com.github.dexecutor.core.DexecutorConfig;
import com.github.dexecutor.core.ExecutionConfig;
import com.github.dexecutor.core.graph.LevelOrderTraversar;
import com.github.dexecutor.core.graph.StringTraversarAction;
import com.github.dexecutor.core.task.ExecutionResults;
import com.github.dexecutor.core.task.Task;
import com.google.common.collect.HashMultiset;

import io.github.pfwikis.layercompiler.description.LCDescription;
import io.github.pfwikis.layercompiler.steps.CheckGeometry;
import io.github.pfwikis.layercompiler.steps.ReadFile;
import io.github.pfwikis.layercompiler.steps.model.LCStepAbstract;
import io.github.pfwikis.layercompiler.steps.model.LCStepAbstract.Ctx;
import io.github.pfwikis.layercompiler.steps.model.TimeSlicedContent;
import io.github.pfwikis.run.Runner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LayersCompiler {

    private final TileCompiler tileCompiler;
    private final File stepsFile = new File("steps.yaml");

    public void compile() throws Exception {
        var lsDescriptions = new YAMLMapper().readValue(stepsFile, LCDescription[].class);

        Map<String, LCStepAbstract> steps = new HashMap<>();
        var pool = Executors.newVirtualThreadPerTaskExecutor();

        try {
            var config = new DexecutorConfig<String, TimeSlicedContent>(pool, id-> {
                return (Task<String, TimeSlicedContent>) Objects.requireNonNull(steps.get(id), "Could not resolve step "+id);
            });
            var executor = new DefaultDexecutor<>(config);

            createSteps(lsDescriptions, steps, executor);

            var sb = new StringBuilder();
    		executor.print(new LevelOrderTraversar<>(), new StringTraversarAction<>(sb));
    		log.info("Execution plan:\n{}", sb.toString());
    
            var results = executor.execute(ExecutionConfig.NON_TERMINATING);
            if(!results.getErrored().isEmpty()) {
                log.error("Failed executions:\n{}",
            		results.getErrored()
            		.stream()
            		.map(er->er.getId()+": "+er.getMessage())
            		.collect(Collectors.joining("\n"))
            	);
                System.exit(-1);
            }
            printTimings(results, steps);
            
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.DAYS);
            FileUtils.deleteQuietly(Runner.TMP_DIR);
        }
    }

    private static class Timings {
    	private Duration time=Duration.ZERO;
    	private Map<String, Duration> subtimes = new HashMap<>();
    }
    private void printTimings(ExecutionResults<String, TimeSlicedContent> results, Map<String, LCStepAbstract> steps) {
    	var map = new HashMap<String, Timings>();
    	for(var result:results.getSuccess()) {
    		var step = steps.get(result.getId());
    		var timings = map.computeIfAbsent(step.getStep(), _->new Timings());
    		timings.time = timings.time.plus(Duration.between(result.getStartTime(), result.getEndTime()));
        	for(var sub:step.getSubTimings().entrySet()) {
        		timings.subtimes.merge(sub.getKey(), sub.getValue(), Duration::plus);
        	}
        }
    	
    	
    	var timings = new StringBuilder();
    	
        for(var result:map.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).toList()) {
        	timings.append("\n").append(result.getKey())
        		.append("\t")
        		.append(result.getValue().time.toSeconds())
        		.append("s");
        	
        	for(var sub:result.getValue().subtimes.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).toList()) {
        		timings
        			.append("\n\t")
        			.append(sub.getKey())
        			.append("\t")
        			.append(sub.getValue().toSeconds())
        			.append("s");
        	}
        }
        log.info("Runtimes:{}", timings);
	}

	private void createSteps(LCDescription[] lsDescriptions, Map<String, LCStepAbstract> steps, DefaultDexecutor<String, TimeSlicedContent> executor) {
    	var requiredCount = HashMultiset.<String>create();
    	var ctx = new Ctx(
            tileCompiler.getOptions(),
            tileCompiler.getGeo(),
            tileCompiler.getOptions().getMappingDataFile()
        );
    	
        for(var lsDescription : lsDescriptions) {
            for(int i=0;i<lsDescription.getSteps().size();i++) {
                var step = lsDescription.getSteps().get(i);
                String name = lsDescription.createName(i);
                if(steps.containsKey(name)) {
                    throw new IllegalStateException("Duplicate name "+name);
                }

                var lcstep = step.create(ctx);
                lcstep.setName(lsDescription.getName());
                lcstep.setStep(step.getStep());
                steps.put(name, lcstep);
                executor.addIndependent(name);

                if(i>0) {
                    var dep = lsDescription.createName(i-1);
                    lcstep.getInputMapping().put("in", dep);
                    executor.addDependency(dep, name);
                    requiredCount.add(dep);
                }
                if(step.getDependsOn().getIn() != null) {
                    var dep = resolveName(step.getDependsOn().getIn(), lsDescriptions);
                    lcstep.getInputMapping().put("in", dep);
                    executor.addDependency(dep, name);
                    requiredCount.add(dep);
                }
                //Create checking step for each reading
                if(step.getStep().equals("READ_FILE")) {
                	var checker = new CheckGeometry();
                	checker.init(ctx);
                	checker.setName(lsDescription.getName()+"-check-geometry");
                	checker.setStep("CHECK_GEOMETRY");
                	requiredCount.add(name);
                	checker.setLayer(((ReadFile)lcstep).getLayer());
                	checker.getInputMapping().put("in", name);
                	steps.put(name+"-check-geometry", checker);
                	executor.addIndependent(name+"-check-geometry");
                	executor.addDependency(name, name+"-check-geometry");
                }

                for(var e:step.getDependsOn().getOtherDependencies().entrySet()) {
                    var dep = resolveName(e.getValue(), lsDescriptions);
                    lcstep.getInputMapping().put(e.getKey(), dep);
                    executor.addDependency(dep, name);
                    requiredCount.add(dep);
                }
            }
        }
        
        for(var required:requiredCount.entrySet()) {
        	Optional.ofNullable(steps.get(required.getElement()))
        		.orElseThrow(()->new IllegalArgumentException("Unknown dependent '"+required.getElement()+"'"))
        		.setNumberOfDependents(required.getCount());
        }
    }

    private String resolveName(String name, LCDescription[] lsDescriptions) {
        if(name.contains("."))
            return name;
        var descr = Arrays.stream(lsDescriptions).filter(lsd->lsd.getName().equals(name)).findAny().get();
        return descr.createName(descr.getSteps().size()-1);
    }
}
