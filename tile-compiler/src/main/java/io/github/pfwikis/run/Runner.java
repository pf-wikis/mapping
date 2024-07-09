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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCContentPath;
import io.github.pfwikis.layercompiler.steps.model.LCStep;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Runner {
    /*package*/ static LCContent run(LCStep step, String command, Object... args) throws IOException {
        return internalRun(step, false, null, command, args);
    }

    /*package*/ static LCContent runPipeOut(LCStep step, String command, Object... args) throws IOException {
        return internalRun(step, true, null, command, args);
    }

    /*package*/ static LCContent runPipeInOut(LCStep step, LCContent in, String command, Object... args) throws IOException {
        return internalRun(step, true, in, command, args);
    }
    
    private static LCContent internalRun(LCStep step, boolean readStdOut, LCContent in, String command, Object... args) throws IOException {
    	File stdOutF = tmpGeojson(step);
    	File errOutF = tmpGeojson(step);
        try(var cmd = Command.of(step, command, args)) {
        	var proc = new ProcessBuilder()
        		.command(cmd.getParts())
        		.redirectError(errOutF)
        		.redirectInput(in==null?Redirect.INHERIT:(in instanceof LCContentPath?Redirect.from(in.toTmpFile(step).toFile()):Redirect.PIPE))
        		.redirectOutput(stdOutF)
	    		.start();
        	
        	if(in != null && !(in instanceof LCContentPath)) {
        		IOUtils.copy(in.toInputStream(), proc.getOutputStream());
        		proc.getOutputStream().close();
        	}
        	int exitValue = proc.waitFor();

        	if(exitValue != 0) {
                throw new RuntimeException("Exited command "+cmd.getParts()+" with non-zero code: "+exitValue);
            }
            LCContent result = LCContent.empty();
            var stdOut = LCContent.from(stdOutF, true);
            var errOut = LCContent.from(errOutF, true);
            
            if(!readStdOut) {
            	log(Level.INFO, stdOut.toJSONString());
            	stdOut.finishUsage();
            }
            else {
            	result = stdOut;
            }
            log(Level.ERROR, errOut.toJSONString());
            errOut.finishUsage();
            
            if(cmd.getResultFile() != null) {
                result = LCContent.from(cmd.getResultFile(), true);
            }

            return result;
        } catch(Exception e) {
            if(in != null) {
                var str = in.toJSONString();
                log.error("Failure for input "+str.substring(0, Math.min(1000, str.length())));
                var tmp = Files.createTempFile("pf-mapping-debug", ".json");
                Files.write(tmp, in.toBytes());
                log.error("Full input in "+tmp.toAbsolutePath());
            }
            if(stdOutF.isFile()) {
            	var stdOut = LCContent.from(stdOutF, true);
            	log(Level.INFO, stdOut.toJSONString());
            	stdOut.finishUsage();
            }
            if(errOutF.isFile()) {
            	var errOut = LCContent.from(errOutF, true);
            	log(Level.ERROR, errOut.toJSONString());
            	errOut.finishUsage();
            }
            throw new IOException(e);
        }
    }

    private static void log(Level level, String msg) {
        if(StringUtils.isEmpty(msg)) return;
        log.atLevel(level).log("std|{}: {}", level.name(), msg);
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
