package io.github.pfwikis.run;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import io.github.pfwikis.layercompiler.steps.model.StepExecutor;
import io.github.pfwikis.layercompiler.steps.model.data.GeoData;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Runner {
	
	private static Semaphore limiter = new Semaphore(8, true);
	public static void setMaximumParallelism(int limit) {
		limiter = new Semaphore(limit, true);
		
	}
	
    /*package*/ static GeoData run(StepExecutor step, String command, Object... args) throws IOException {
    	limiter.acquireUninterruptibly();
    	try(	var cmd = Command.of(step, command, args);
    			var stdOut = new StdHelper("std", step);
    			var stdErr = new StdHelper("err", step)) {
    		
    		var pump = new PumpStreamHandler(stdOut.getStream(), stdErr.getStream(), null);
    		pump.setStopTimeout(Duration.ofSeconds(10));
        	var executor = DefaultExecutor.builder()
        		.setExecuteStreamHandler(pump)
        		.get();
        	
        	var result = new CompletableFuture<Integer>();
        	executor.execute(cmd.toCommandLine(), new ExecuteResultHandler() {
				@Override
				public void onProcessFailed(ExecuteException e) {
					result.completeExceptionally(e);
				}
				
				@Override
				public void onProcessComplete(int exit) {
					result.complete(exit);
				}
			});
        	
        	try {
        		while(result.copy().completeOnTimeout(null, 10, TimeUnit.SECONDS).get()==null) {
            		stdOut.intermediatePrint();
            		stdErr.intermediatePrint();
            	}
        		
        		if(result.get() != 0) {
        			throw new RuntimeException("Exitcode "+result.get());
        		}
        		
        		stdOut.intermediatePrint();
        		stdErr.intermediatePrint();
        		
        		GeoData output = GeoData.empty();
 	            if(cmd.getResultFile() != null) {
 	            	output = GeoData.from(cmd.getResultFile());
 	            }
 	            return output;
        	} catch(Exception e) {
        		var out = stdOut.toString();
        		var err = stdErr.toString();
        		var sb = new StringBuilder()
        				.append("Exited tool failed for ")
        				.append(Thread.currentThread().getName())
        				.append(": ")
        				.append(cmd);
        		if(StringUtils.isNotBlank(out))
        			sb.append("\nout: ").append(out);
        		if(StringUtils.isNotBlank(err))
        			sb.append("\nerr: ").append(err);
        		throw new IOException(sb.toString(), e);
        	}
    	}
    	finally {
    		limiter.release();
    	}
    }

    public static final File TMP_DIR;
    static {
    	try {
    		String dirname = "pathfinder-mapping-"+(Instant.now().toEpochMilli()/1000);
	    	File dir = null;
	    	try {
	    		dir = Files.createDirectory(Path.of("//wsl$/Ubuntu/tmp/"+dirname)).toFile();
	    	} catch (Exception e) {
	        	dir = Files.createTempDirectory(dirname).toFile();
	    	}
	    	TMP_DIR = dir;
	    	FileUtils.forceDeleteOnExit(TMP_DIR);
    	} catch (IOException e2) {
            throw new RuntimeException(e2);
        }
    }

    private static final ConcurrentMap<String, AtomicInteger> TMP_COUNTER = new ConcurrentHashMap<>(); 

    public static File tmpGeojson(StepExecutor step, OutFile outFile) {
    	String prefix = step!=null?(step.getId()+"_"):"";
    	int uniqueCounter = TMP_COUNTER.computeIfAbsent(prefix, _->new AtomicInteger(1)).getAndIncrement();
        var f = new File(TMP_DIR, prefix+"%02d.%s".formatted(uniqueCounter, outFile.ext));
        f.deleteOnExit();
        return f;
    }

    public static record TmpGeojson(String commandPrefix, GeoData content){
        public TmpGeojson(GeoData content) {
            this("", content);
        }
    }
    public static record OutFile(String commandPrefix, String ext){
    	public OutFile(String commandPrefix) {
            this(commandPrefix, "geojson");
        }
        public OutFile() {
            this("", "geojson");
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static class Command implements Closeable {

        private final List<String> parts = new ArrayList<>();
        private final StepExecutor step;
        private File resultFile;
        private ToolVariant toolVariant;

        public static Command of(StepExecutor step, String command, Object... commandParts) throws IOException {
            var result = new Command(step);
            result.toolVariant = ToolVariant.getFor(command);
            result.addCommandParts(new String[] {command});
            result.addCommandParts(commandParts);
            result.toolVariant.modifyArguments(result.parts);
            log.info(String.join(" ", result.parts));
            return result;
        }

        public CommandLine toCommandLine() {
			var cmd = new CommandLine(parts.getFirst());
			parts.stream().skip(1).forEach(p->cmd.addArgument(p, false));
			return cmd;
		}
        
        @Override
        public String toString() {
        	return parts.stream().collect(Collectors.joining(" "));
        }

		private void addCommandParts(Object[] commandParts) throws IOException {
            for(var part : commandParts) {
            	Objects.requireNonNull(part, ()->"null Argument when executing "+Arrays.toString(commandParts));
            	if(part instanceof String v) {
                	parts.add(v.replace("\n", ""));
                }
            	else if(part instanceof GeoData content) {
                	parts.add(toolVariant.translateFile(content.toTmpFile(step)));
                }
                else if(part instanceof TmpGeojson json) {
                    parts.add(json.commandPrefix()+toolVariant.translateFile(json.content().toTmpFile(step)));
                }
                else if(part instanceof OutFile outFile) {
                    resultFile = tmpGeojson(step, outFile);
                    parts.add(outFile.commandPrefix()+toolVariant.translateFile(resultFile.toPath()));
                }
                else if(part instanceof List<?> l) {
                	addCommandParts(l.toArray());
                }
                else if(part instanceof File f) {
                	parts.add(toolVariant.translateFile(f.toPath()));
                }
                else if(part instanceof Path p) {
                	parts.add(toolVariant.translateFile(p));
                }
                else if(part instanceof String[] arr) {
                    addCommandParts(arr);
                }
                else if(part instanceof Object[] arr) {
                    addCommandParts(arr);
                }
                else if(part instanceof Collection<?> col) {
                    addCommandParts(col.toArray(Object[]::new));
                }
                else {
                    throw new IllegalStateException("Can't handle command part "+part.getClass().getName());
                }
            }
        }

        @Override
        public void close() throws IOException {
        }
    }
}
