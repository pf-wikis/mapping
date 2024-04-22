package io.github.pfwikis.run;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;

import io.github.pfwikis.layercompiler.steps.model.LCContent;
import io.github.pfwikis.layercompiler.steps.model.LCContentPath;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Runner {
    /*package*/ static LCContent run(String command, Object... args) throws IOException {
        return internalRun(false, null, command, args);
    }

    /*package*/ static LCContent runPipeOut(String command, Object... args) throws IOException {
        return internalRun(true, null, command, args);
    }

    /*package*/ static LCContent runPipeInOut(LCContent in, String command, Object... args) throws IOException {
        return internalRun(true, in, command, args);
    }
    
    private static LCContent internalRun(boolean readStdOut, LCContent in, String command, Object... args) throws IOException {
    	File stdOutF = tmpGeojson();
    	File errOutF = tmpGeojson();
        try(var cmd = Command.of(command, args)) {
        	var proc = new ProcessBuilder()
        		.command(cmd.getParts())
        		.redirectError(errOutF)
        		.redirectInput(in==null?Redirect.INHERIT:(in instanceof LCContentPath?Redirect.from(in.toTmpFile().toFile()):Redirect.PIPE))
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
            	log(Level.INFO, errOut.toJSONString());
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
            TMP_DIR = Files.createTempDirectory("pathfinder-mapping-").toFile();
            FileUtils.forceDeleteOnExit(TMP_DIR);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final AtomicInteger TMP_COUNTER = new AtomicInteger();

    public static File tmpGeojson() {
        var f = new File(TMP_DIR, TMP_COUNTER.getAndIncrement()+".geojson");
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
    private static class Command implements Closeable {

        private final List<String> parts = new ArrayList<>();
        private File resultFile;
        private ToolVariant toolVariant;

        public static Command of(String command, Object... commandParts) throws IOException {
            var result = new Command();
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
                if(part instanceof LCContent content) {
                	parts.add(toolVariant.translateFile(content.toTmpFile()));
                }
                else if(part instanceof TmpGeojson json) {
                    parts.add(json.commandPrefix()+toolVariant.translateFile(json.content().toTmpFile()));
                }
                else if(part instanceof OutGeojson json) {
                    resultFile = tmpGeojson();
                    parts.add(json.commandPrefix()+toolVariant.translateFile(resultFile.toPath()));
                }
                else if(part instanceof String v) {
                	parts.add(v.replace("\n", ""));
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
