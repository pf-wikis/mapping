package io.github.pfwikis;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.ZoneId;
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

import io.github.pfwikis.layercompiler.cache.CacheManager;
import io.github.pfwikis.layercompiler.description.ExecutionPlan;
import io.github.pfwikis.layercompiler.steps.model.content.Content;
import io.github.pfwikis.run.Runner;
import io.github.pfwikis.util.TimingStats;
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
    
            long wallStartNanos = System.nanoTime();
            long cpuStartNanos = processCpuTimeNanos();
            var results = executor.execute(ExecutionConfig.NON_TERMINATING);
            long wallNanos = System.nanoTime() - wallStartNanos;
            long cpuEndNanos = processCpuTimeNanos();

            if(!results.getErrored().isEmpty()) {
                log.error("Failed executions:\n{}",
            		results.getErrored()
            		.stream()
            		.map(er->er.getId()+": "+er.getMessage())
            		.collect(Collectors.joining("\n"))
            	);
                System.exit(-1);
            }

            var wallTime = Duration.ofNanos(Math.max(0, wallNanos));
            Duration cpuTime = cpuStartNanos >= 0 && cpuEndNanos >= 0
                ? Duration.ofNanos(Math.max(0, cpuEndNanos - cpuStartNanos))
                : null;
            printTimings(results, plan, wallTime, cpuTime);
            CacheManager.INSTANCE.sweep();
            
        } finally {
            pool.shutdown();
            pool.awaitTermination(1, TimeUnit.DAYS);
            FileUtils.deleteQuietly(Runner.TMP_DIR);
        }
    }

	private static class Timings {
		private TimingStats stats = TimingStats.empty();
		private Map<String, TimingStats> subtimes = new HashMap<>();
	}

	private void printTimings(
			ExecutionResults<String, Content> results,
			ExecutionPlan plan,
			Duration wallTime,
			Duration cpuTime) {
		var map = new HashMap<String, Timings>();
		for(var result:results.getSuccess()) {
			var step = plan.getStep(result.getId());
			var timings = map.computeIfAbsent(step.getStep(), _->new Timings());
			var start = result.getStartTime().atZone(ZoneId.systemDefault()).toInstant();
			var end = result.getEndTime().atZone(ZoneId.systemDefault()).toInstant();
			timings.stats = timings.stats.plus(TimingStats.of(Duration.between(start, end), start, end));
			for(var sub:step.getExecutor().getSubTimings().entrySet()) {
				timings.subtimes.merge(sub.getKey(), sub.getValue(), TimingStats::plus);
			}
		}

		var timings = new StringBuilder();

		for(var result:map.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).toList()) {
			timings.append("\n").append(result.getKey())
				.append("\twork=")
				.append(result.getValue().stats.work().toSeconds())
				.append("s")
				.append("\twall=")
				.append(result.getValue().stats.wall().toSeconds())
				.append("s");

			for(var sub:result.getValue().subtimes.entrySet().stream().sorted(Comparator.comparing(Entry::getKey)).toList()) {
				timings
					.append("\n\t")
					.append(sub.getKey())
					.append("\twork=")
					.append(sub.getValue().work().toSeconds())
					.append("s")
					.append("\twall=")
					.append(sub.getValue().wall().toSeconds())
					.append("s");
			}
		}
		log.info("Runtimes (work=sum of task durations, wall=first start to last end):{}", timings);
		log.info("Total walltime: {}s, process CPU time: {}",
				wallTime.toSeconds(), cpuTime == null ? "n/a" : cpuTime.toSeconds() + "s");
	}

	private static long processCpuTimeNanos() {
		var bean = ManagementFactory.getOperatingSystemMXBean();
		if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
			return os.getProcessCpuTime();
		}
		return -1;
	}
}
