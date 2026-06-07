package io.github.pfwikis;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import com.github.dexecutor.core.DefaultDexecutor;
import com.github.dexecutor.core.DexecutorConfig;
import com.github.dexecutor.core.ExecutionConfig;
import com.github.dexecutor.core.graph.LevelOrderTraversar;
import com.github.dexecutor.core.graph.StringTraversarAction;
import com.github.dexecutor.core.task.ExecutionResults;

import io.github.pfwikis.layercompiler.description.ExecutionPlan;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Runner;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LayersCompiler {

    public void compile() throws Exception {
    	
        var pool = Executors.newVirtualThreadPerTaskExecutor();

        try {
        	
        	var plan = ExecutionPlan.parseFromFile();
        	
            var config = new DexecutorConfig<String, Content>(pool, id-> plan.getStep(id).getExecutor());
            var executor = new DefaultDexecutor<>(config);
            plan.createExecutions(executor);

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
            printTimings(results, plan);
            
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
    private void printTimings(ExecutionResults<String, Content> results, ExecutionPlan plan) {
    	var map = new HashMap<String, Timings>();
    	for(var result:results.getSuccess()) {
    		var step = plan.getStep(result.getId());
    		var timings = map.computeIfAbsent(step.getStep(), _->new Timings());
    		timings.time = timings.time.plus(Duration.between(result.getStartTime(), result.getEndTime()));
        	for(var sub:step.getExecutor().getSubTimings().entrySet()) {
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
}
