package io.github.pfwikis.run;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Runner {
    /*package*/ static LCContent run(LCStep step, String command, Object... args) throws IOException {
        return internalRun(step, command, args);
    }
    
    private static LCContent internalRun(LCStep step, String command, Object... args) throws IOException {
    	try(
    			var stdOut = new StdHelper("std", step);
            	var stdErr = new StdHelper("err", step);) {
	        try(var cmd = Command.of(step, command, args);) {
	        	var proc = new ProcessBuilder()
	        		.command(cmd.getParts())
	        		.redirectError(stdOut.getFile())
	        		.redirectInput(Redirect.INHERIT)
	        		.redirectOutput(stdErr.getFile())
		    		.start();
	        	
	        	while(!proc.waitFor(1, TimeUnit.SECONDS)) {
	        		stdOut.intermediatePrint();
	        		stdErr.intermediatePrint();
	        	}
	        	
	        	int exitValue = proc.waitFor();
	        	
	        	//print any remaining content
	        	stdOut.intermediatePrint();
	        	stdErr.intermediatePrint();
	
	        	if(exitValue != 0) {
	                throw new IOException("Exited command "+cmd.getParts()+" with non-zero code: "+exitValue);
	            }
	            LCContent result = LCContent.empty();
	
	            if(cmd.getResultFile() != null) {
	                result = LCContent.from(cmd.getResultFile(), true);
	            }
	            return result;
	        } catch(Exception e) {
	        	if(e instanceof IOException ioe && e.getMessage().startsWith("Exited command ")) {
	        		throw ioe;
	        	}
        		stdOut.intermediatePrint();
        		stdErr.intermediatePrint();
        		throw new IOException(e);
	        }
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

    public static File tmpGeojson(LCStep step) {
    	String prefix = step!=null?(step.getId()+"_"):"";
    	int uniqueCounter = TMP_COUNTER.computeIfAbsent(prefix, key->new AtomicInteger(1)).getAndIncrement();
        var f = new File(TMP_DIR, prefix+"%02d.geojson".formatted(uniqueCounter));
        f.deleteOnExit();
        return f;
    }

    public static record TmpGeojson(String commandPrefix, LCContent content){
        public TmpGeojson(LCContent content) {
            this("", content);
        }
    }
    public static record OutGeojson(String commandPrefix){
        public OutGeojson() {
            this("");
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static class Command implements Closeable {

        private final List<String> parts = new ArrayList<>();
        private final LCStep step;
        private File resultFile;
        private ToolVariant toolVariant;

        public static Command of(LCStep step, String command, Object... commandParts) throws IOException {
            var result = new Command(step);
            result.toolVariant = ToolVariant.getFor(command);
            result.addCommandParts(new String[] {command});
            result.addCommandParts(commandParts);
            result.toolVariant.modifyArguments(result.parts);
            log.info(String.join(" ", result.parts));
            return result;
        }

        private void addCommandParts(Object[] commandParts) throws IOException {
            for(var part : commandParts) {
            	Objects.requireNonNull(part, ()->"null Argument when executing "+Arrays.toString(commandParts));
            	if(part instanceof String v) {
                	parts.add(v.replace("\n", ""));
                }
            	else if(part instanceof LCContent content) {
                	parts.add(toolVariant.translateFile(content.toTmpFile(step)));
                }
                else if(part instanceof TmpGeojson json) {
                    parts.add(json.commandPrefix()+toolVariant.translateFile(json.content().toTmpFile(step)));
                }
                else if(part instanceof OutGeojson json) {
                    resultFile = tmpGeojson(step);
                    parts.add(json.commandPrefix()+toolVariant.translateFile(resultFile.toPath()));
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
